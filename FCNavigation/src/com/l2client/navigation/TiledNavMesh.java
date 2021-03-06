package com.l2client.navigation;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import jme3tools.optimize.GeometryBatchFactory;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.scene.shape.Line;
import com.jme3.util.BufferUtils;
import com.l2client.navigation.Line2D.LINE_CLASSIFICATION;

/**
 * A navigation.NavMesh wrapper with functionality for tiled navmeshes.
 * 
 * @author tmi
 * 
 */
public class TiledNavMesh extends NavMesh {

	private static Logger log = Logger.getLogger(TiledNavMesh.class.getName());
	private static final float TERRAIN_SIZE = 256f;//IAREA.TERRAIN_SIZE
	private static final float CENTER_OFFSET = 0f;
	//BOUNDS hit flag for bitwise flagging on more than one bounds hit
	private static final int BOUNDS_LEFT = 1;
	private static final int BOUNDS_RIGHT = 2;
	private static final int BOUNDS_TOP = 4;
	private static final int BOUNDS_BOTTOM = 8;
	private static final int BOUNDS_TOP_LEFT = 5;
	private static final int BOUNDS_TOP_RIGHT = 6;
	private static final int BOUNDS_BOTTOM_LEFT = 9;
	private static final int BOUNDS_BOTTOM_RIGHT = 10;
	//BORDER slots 8 tiles clockwise starting from top left
	private static final int BORDER_TOP_LEFT = 0;
	private static final int BORDER_TOP = 1;
	private static final int BORDER_TOP_RIGHT = 2;
	private static final int BORDER_RIGHT = 3;
	private static final int BORDER_BOTTOM_RIGHT = 4;
	private static final int BORDER_BOTTOM = 5;
	private static final int BORDER_BOTTOM_LEFT = 6;
	private static final int BORDER_LEFT = 7;

	private Vector3f worldTranslation = Vector3f.ZERO;
	private volatile int hash = 0;

	private Line2D top = null;
	private Line2D right = null;
	private Line2D bottom = null;
	private Line2D left = null;

	//A map of cell - borderflag entries
	//FIXME this is empty !!! on l2j tiles, why
	HashMap<Cell, Integer> borders = new HashMap<Cell, Integer>();
	//FIXME initialization
	private ArrayList<HashSet<Cell>> allBorders = new ArrayList<HashSet<Cell>>(8);
	
	public TiledNavMesh(){
		for(int i=0;i<8;i++)
			allBorders.add(new HashSet<Cell>());	
	}
	
	static int getHashCode(float x, float z){
		//FIXME duplicate from Tile class getTileFromWorld?Positions
		int tx = (((int)x)>>8)+160;// +160 (20*8 tiles) because 0 in x is in tile 160 not in tile 0
		int tz = (((int)z)>>8)+144; //+144 because 0 in z is in tile 144 not in tile 0	
		if(tx >0 && tz > 0){
			return ( 100000000 + (tx*10000) +tz );
		} else {
			log.severe("Trying to hash negative values, check the setup: tx:"+tx+":"+x+" ty:"+tz+":"+z);
			return -1;
		}	
	}
	
    /**
     * HashCode build by 1 xxxx yyyy as a number, x and y should be no bigger than 9999(non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
	@Override
	public int hashCode() {
		if(hash != 0)
			return hash;
		else {
			hash = getHashCode(worldTranslation.x, worldTranslation.z);
			if(hash < 0)
				hash = super.hashCode();
			return hash;
		}
	}

	public void write(JmeExporter e) throws IOException {

		super.write(e);
		OutputCapsule capsule = e.getCapsule(this);
		capsule.write(top, "top", null);
		capsule.write(right, "right", null);
		capsule.write(bottom, "bottom", null);
		capsule.write(left, "left", null);
		capsule.write(worldTranslation, "worldpos", null);
		
		//FIXME ev. override ? and do it only once?
		HashMap<Cell, Integer> tmp = new HashMap<Cell, Integer>();
		for(int i =0; i<mCellArray.length; i++)
			tmp.put(mCellArray[i], i);
		
		int r=0;
		int[] vals = new int[borders.keySet().size()];
		for(Cell i : borders.keySet())
			vals[r++]=tmp.get(i);
		capsule.write(vals, "borders_keys",null);
		r=0;
		for(Integer i : borders.values())
			vals[r++]=i;
		capsule.write(vals, "borders_values",null);
			
		for(int i=0;i<8;i++){
			vals = new int[allBorders.get(i).size()];
			r = 0;
			for(Cell c : allBorders.get(i))
				vals[r++]=tmp.get(c);
			capsule.write(vals, "allborders"+i,null);
		}
	}

	public void read(JmeImporter e) throws IOException {
		super.read(e);
		InputCapsule capsule = e.getCapsule(this);
		top = (Line2D) capsule.readSavable("top", null);
		right = (Line2D) capsule.readSavable("right", null);
		bottom = (Line2D) capsule.readSavable("bottom", null);
		left = (Line2D) capsule.readSavable("left", null);
		worldTranslation = (Vector3f) capsule.readSavable("worldpos", null);
		if(worldTranslation != null)
			log.fine("TiledNavMesh loaded at:"+worldTranslation);
		//FIXME loading and storing of border information
		int[] bKeys = capsule.readIntArray("borders_keys",null);
		int[] vKeys = capsule.readIntArray("borders_values",null);

		for(int i=0; i<bKeys.length;i++)
			borders.put(mCellArray[i], vKeys[i]);
		
		for(int i=0;i<8;i++){
			int[] b = capsule.readIntArray("allborders"+i,null);
			for(int c : b)
				allBorders.get(i).add(mCellArray[c]);
		}
	}

	/**
	 * Creates the NavMesh for the vertices of the passed TriMesh, by also considering world translation on creation
	 * @param tri
	 */
	//TODO testcase needed for translated trimesh
	public void loadFromGeom(Geometry geom) {
		com.jme3.scene.Mesh tri = geom.getMesh();
		loadFromMesh(tri, geom.getWorldTranslation(), false);
	}
	
	/**
	 * call before attaching or anything
	 * @param positions		Array of Vector3f positions in local coordinates
	 * @param indices		Array of indices to triangles in the positions array
	 * @param worldtrans	The final world translation of the tile center
	 */
    public void loadFromData(Vector3f[] positions, short[][] indices, Vector3f worldtrans){

		this.worldTranslation = worldtrans.clone();
        Vector3f offset = Vector3f.ZERO;
        int down = 0;
        int total = 0;
        
		clearBorderPoints();
		createBounds();
		
        Plane up = new Plane();
        up.setPlanePoints(Vector3f.UNIT_X, Vector3f.ZERO, Vector3f.UNIT_Z);
        up.getNormal();

        Vector3f vertA = null;
        Vector3f vertB = null;
        Vector3f vertC = null;
        Cell c = null;
        for (int i = 0; i < indices.length; i++) {
        	total++;
            vertA = positions[indices[i][0]];
            vertB = positions[indices[i][1]];
            vertC = positions[indices[i][2]];
            
            Plane p = new Plane();
            p.setPlanePoints(vertA, vertB, vertC);
            if (up.pseudoDistance(p.getNormal()) <= 0.0f) {
                down++;
                continue;
            }
            
			borderCorrection(vertA, offset);
            borderCorrection(vertB, offset);
            borderCorrection(vertC, offset);
            
            vertA = vertA.add(worldtrans);
            vertB = vertB.add(worldtrans);
            vertC = vertC.add(worldtrans);

            c = AddCell(vertA, vertB, vertC);
            storeBorderCell(c);
        }

        LinkCells();
        log.warning("Ignored "+down+" of "+total+" faces facing downward in the mesh");
    }
 	
    /**
     * 
     * @param mesh				A mesh
     * @param worldtrans		The final center of the tile used for border creation
     * @param isRelative		true if the mesh is in local coordinates, false if already translated to worldtrans
     */
    public void loadFromMesh(Mesh mesh, Vector3f worldtrans, boolean isRelative) {
    	worldTranslation = worldtrans.clone();
		Vector3f offset;
		
		int down = 0;
		int slope = 0;
		int total = 0;
		float maxSlope = 82f;
		
		if(isRelative)
			offset = Vector3f.ZERO;
		else
			offset = worldtrans;
		
		clearBorderPoints();
		createBounds();

        Plane up = new Plane();
        up.setPlanePoints(Vector3f.UNIT_X, Vector3f.ZERO, Vector3f.UNIT_Z);
        up.getNormal();
        float minNormalY = (float)Math.cos(Math.abs(maxSlope)/180 * Math.PI);

        IndexBuffer ib = mesh.getIndexBuffer();
        FloatBuffer pb = mesh.getFloatBuffer(Type.Position);
        pb.clear();
        for (int i = 0; i < mesh.getTriangleCount()*3; i+=3){
        	total++;
            int i1 = ib.get(i+0);
            int i2 = ib.get(i+1);
            int i3 = ib.get(i+2);
            Vector3f a = new Vector3f();
            Vector3f b = new Vector3f();
            Vector3f c = new Vector3f();
            BufferUtils.populateFromBuffer(a, pb, i1);
            BufferUtils.populateFromBuffer(b, pb, i2);
            BufferUtils.populateFromBuffer(c, pb, i3);
            
            borderCorrection(a, offset);
            borderCorrection(b, offset);
            borderCorrection(c, offset);
        
            if(isRelative){
	            a=a.add(worldtrans);
	            b=b.add(worldtrans);
	            c=c.add(worldtrans);
            }
            
            Plane p = new Plane();
            p.setPlanePoints(a, b, c);
            if (up.pseudoDistance(p.getNormal()) <= 0.0f) {
            	down++;
                continue;
            }
            if (p.getNormal().y < minNormalY){
            	slope++;
            	continue;
            }

            storeBorderCell(AddCell(a, b, c));
        }

        LinkCells();
        log.warning("Ignored "+down+" faces facing downward and "+slope+" with too high slope of "+total+" faces in the mesh");
    }

	/**
	 * Vertices being near the border, currently 0.01f away will be clamped to the 
	 * border. Input values are expected to be in local coordinates (before translating to the final world position)
	 * @param v vertex who's values should be checked and corrected, the values can change
	 */
	private void borderCorrection(Vector3f v, Vector3f offset) {
		float borderDelta = 0.5f;// if within this range to a border it will
									// be clamped to the border
		v.x = clampToBorder(v.x, borderDelta, offset.x);
		//hey, only in x and z , dont clamp the height !?!
//		v.y = clampToBorder(v.y, borderDelta, offset.y);
		v.z = clampToBorder(v.z, borderDelta, offset.z);
	}

	/**
	 * clamp a value to the border if it is within the specified delta away from the border side
	 * @param x value which possibly is near a border, in local coordinates
	 * @param borderDelta
	 * @return
	 */
	//FIXME this is currently totaly wrong and just working for l2j meshes with center at 0/0, and extents from 0/0 to 256/256 :-( this has to be cleaned up and made robust for completly offset and relative meshes
	private float clampToBorder(float x, float borderDelta, float offset) {
		if (FastMath.abs(x - CENTER_OFFSET - offset) <= borderDelta)
			return CENTER_OFFSET + offset;
		if (FastMath.abs(x - TERRAIN_SIZE - CENTER_OFFSET - offset) <= borderDelta)
			return TERRAIN_SIZE + CENTER_OFFSET + offset;
		return x;
	}

	/**
	 * when bounds for top is set we initialize the border point arrays otherwise we 
	 * clear any and set them to null
	 */
	private void clearBorderPoints() {
		//any bounds set at all?
		borders.clear();
		for(HashSet<Cell> set  : allBorders)
			set.clear();
	}
	
	/**
	 * Calculates the border lines. Requirement: worldtranslation is set.
	 */
	private void createBounds(){
//		top = new Line2D(
//				new Vector2f(worldTranslation.x-TERRAIN_SIZE_HALF, worldTranslation.z-TERRAIN_SIZE_HALF), 
//				new Vector2f(worldTranslation.x+TERRAIN_SIZE_HALF, worldTranslation.z-TERRAIN_SIZE_HALF) 
//		);
//		right = new Line2D(top.EndPointB(),
//				new Vector2f(worldTranslation.x+TERRAIN_SIZE_HALF, worldTranslation.z+TERRAIN_SIZE_HALF) 
//		);
//		bottom = new Line2D(right.EndPointB(), 
//				new Vector2f(worldTranslation.x-TERRAIN_SIZE_HALF, worldTranslation.z+TERRAIN_SIZE_HALF) 
//		);
//		left = new Line2D(bottom.EndPointB(),top.EndPointA());
		//l2j tiles are top left 0/0 and bottom right 256/256
		top = new Line2D(
		new Vector2f(worldTranslation.x+CENTER_OFFSET, worldTranslation.z+CENTER_OFFSET), 
		new Vector2f(worldTranslation.x+TERRAIN_SIZE+CENTER_OFFSET, worldTranslation.z+CENTER_OFFSET) 
		);
		right = new Line2D(top.EndPointB(),
		new Vector2f(worldTranslation.x+TERRAIN_SIZE+CENTER_OFFSET, worldTranslation.z+TERRAIN_SIZE+CENTER_OFFSET) 
		);
		bottom = new Line2D(right.EndPointB(), 
		new Vector2f(worldTranslation.x+CENTER_OFFSET, worldTranslation.z+TERRAIN_SIZE+CENTER_OFFSET) 
		);
		left = new Line2D(bottom.EndPointB(),top.EndPointA());
		log.finest(this+" top:"+top);
		log.finest(this+" right:"+right);
		log.finest(this+" bottom:"+bottom);
		log.finest(this+" left:"+left);
	}

	private HashSet<Cell> getBorderCells(int where) {
		HashSet<Cell> ret = new HashSet<Cell>();
		switch (where) {
		case BOUNDS_LEFT:
			ret.addAll(allBorders.get(BORDER_TOP_LEFT));ret.addAll(allBorders.get(BORDER_LEFT));ret.addAll(allBorders.get(BORDER_BOTTOM_LEFT));break;
		case BOUNDS_RIGHT:
			ret.addAll(allBorders.get(BORDER_TOP_RIGHT));ret.addAll(allBorders.get(BORDER_RIGHT));ret.addAll(allBorders.get(BORDER_BOTTOM_RIGHT));break;
		case BOUNDS_TOP:
			ret.addAll(allBorders.get(BORDER_TOP_LEFT));ret.addAll(allBorders.get(BORDER_TOP));ret.addAll(allBorders.get(BORDER_TOP_RIGHT));break;
		case BOUNDS_BOTTOM:
			ret.addAll(allBorders.get(BORDER_BOTTOM_LEFT));ret.addAll(allBorders.get(BORDER_BOTTOM));ret.addAll(allBorders.get(BORDER_BOTTOM_RIGHT));break;
		case BOUNDS_TOP_LEFT:
			ret.addAll(allBorders.get(BORDER_LEFT));ret.addAll(allBorders.get(BORDER_TOP_LEFT));ret.addAll(allBorders.get(BORDER_TOP));break;
		case BOUNDS_TOP_RIGHT:
			ret.addAll(allBorders.get(BORDER_TOP));ret.addAll(allBorders.get(BORDER_TOP_RIGHT));ret.addAll(allBorders.get(BORDER_RIGHT));break;
		case BOUNDS_BOTTOM_LEFT:
			ret.addAll(allBorders.get(BORDER_LEFT));ret.addAll(allBorders.get(BORDER_BOTTOM_LEFT));ret.addAll(allBorders.get(BORDER_BOTTOM));break;
		case BOUNDS_BOTTOM_RIGHT:
			ret.addAll(allBorders.get(BORDER_BOTTOM));ret.addAll(allBorders.get(BORDER_BOTTOM_RIGHT));ret.addAll(allBorders.get(BORDER_RIGHT));break;
		}
		return ret;
	}
	
	/**
	 * checks if the any point of the cell is on, or outside our bounds
	 * @param start start point in world coords
	 * @param end end point in world coords
	 * @return 1-4 for top, right, bottom, left lines or 0 if start to end lie not on bounds
	 */
	private void storeBorderCell(Cell c) {
//		if (hasBounds()) {
			int bound = 0;
			int fbound = 0;
			//for each Vertex of a cell
			for (Vector3f v : c.m_Vertex) {
				//check the bounds it crosses
				bound = getBoundingSide(v.x, v.z);
				
				//add cell to the corresponding quadrant ( could be added several times so a hashset is used)
				switch (bound) {
				case BOUNDS_LEFT:
					allBorders.get(BORDER_LEFT).add(c);break;
				case BOUNDS_RIGHT:
					allBorders.get(BORDER_RIGHT).add(c);break;
				case BOUNDS_TOP:
					allBorders.get(BORDER_TOP).add(c);break;
				case BOUNDS_BOTTOM:
					allBorders.get(BORDER_BOTTOM).add(c);break;
				case BOUNDS_TOP_LEFT:
					allBorders.get(BORDER_TOP_LEFT).add(c);break;
				case BOUNDS_TOP_RIGHT:
					allBorders.get(BORDER_TOP_RIGHT).add(c);break;
				case BOUNDS_BOTTOM_LEFT:
					allBorders.get(BORDER_BOTTOM_LEFT).add(c);break;
				case BOUNDS_BOTTOM_RIGHT:
					allBorders.get(BORDER_BOTTOM_RIGHT).add(c);break;
				}
				//add the bounds for this vertex to the overall bounds of this cell
				fbound |= bound;
			}
			//add it to the total of border cells with the overall bounds indicator
			borders.put(c, fbound);

//		}
	}
	
	/**
	 * is this mesh responsible for the mesh navigation ?
	 * @param pos
	 * @return
	 */
	//TODO consider decoupling from TerrainTriMesh 
	public boolean isPointInTile(float posX, float posZ) {
//		//would it possibly be inside?
//		if(posX <=(worldTranslation.x+TERRAIN_SIZE_HALF) &&
//			posX >= (worldTranslation.x -TERRAIN_SIZE_HALF) &&
//			posZ <=(worldTranslation.z+TERRAIN_SIZE_HALF) &&
//			posZ >= (worldTranslation.z -TERRAIN_SIZE_HALF)) {
//			return true;
//		}
		//l2j tiles are top left 0/0 and bottom right 256/256
		//would it possibly be inside?
		if(posX >=(worldTranslation.x+CENTER_OFFSET) &&
		posX <= (worldTranslation.x + TERRAIN_SIZE + CENTER_OFFSET) &&
		posZ >=(worldTranslation.z+CENTER_OFFSET) &&
		posZ <= (worldTranslation.z + TERRAIN_SIZE+CENTER_OFFSET)) {
		return true;
	}
		return false;
	}
	
	private int getBoundingSide(float x, float z){
		int bounds = 0;
////		if (hasBounds()) {
//				//left ?
//				if(x <=(worldTranslation.x-TERRAIN_SIZE_HALF))
//					bounds |= BOUNDS_LEFT;
//				//right ?
//				if(x >= (worldTranslation.x+TERRAIN_SIZE_HALF))
//					bounds |= BOUNDS_RIGHT;
//				//top ?
//				if(z <=(worldTranslation.z-TERRAIN_SIZE_HALF))
//					bounds |= BOUNDS_TOP;
//				//bottom ?
//				if(z >= (worldTranslation.z+TERRAIN_SIZE_HALF))
//					bounds |= BOUNDS_BOTTOM;
////		}
		//l2j tiles are top left 0/0 and bottom right 256/256
		//left ?
		if(x <=(worldTranslation.x+CENTER_OFFSET))
			bounds |= BOUNDS_LEFT;
		//right ?
		if(x >= (worldTranslation.x+TERRAIN_SIZE+CENTER_OFFSET))
			bounds |= BOUNDS_RIGHT;
		//top ?
		if(z <=(worldTranslation.z+CENTER_OFFSET))
			bounds |= BOUNDS_TOP;
		//bottom ?
		if(z >= (worldTranslation.z+TERRAIN_SIZE+CENTER_OFFSET))
			bounds |= BOUNDS_BOTTOM;
		return bounds;
	}

//	//FIXME nav mesh switching the new way ( when next cell is not on this mesh)
//	private Cell ResolveMotionOnMesh(Vector3f StartPos, Cell StartCell, Vector3f EndPos) {
//		if(borders.containsKey(StartCell)) {
//			if(isPointInTile(EndPos.x,EndPos.z))
//				return this.mesh.ResolveMotionOnMesh(StartPos, StartCell, EndPos);
//			else{
//				if (isPointInTile(StartPos.x, StartPos.z)) {
//					Cell c = Singleton.get().getNavManager().FindClosestCell(
//							EndPos, true);
//					if (c != null)
//						c.MapVectorHeightToCell(EndPos);
//					
//					return c;
//				} else {
//					//FIXME switch navmesh!
//					return null;
//				}
//			}
//		} else
//			return this.mesh.ResolveMotionOnMesh(StartPos, StartCell, EndPos);
//	}

	
//	//FIXME remove this, only needed for first time placemenet otherwise path used
//	public Cell FindClosestCell(Vector3f Point) {
//		if(isPointInTile(Point.x,Point.z))
//			return this.mesh.FindClosestCell(Point);
//		
//		return null;
////removed circular loop !!
////		return Singleton.get().getNavManager().FindClosestCell(Point);
//	}

	boolean isBorderCell(Cell c) {
		return borders.containsKey(c);
	}
	
	boolean isBorderCell(int cell) {
		Cell c = getCell(cell);
		return borders.containsKey(c);
	}

	//distance between must be within SimpleTerrainManger.TERRAIN_SIZE
	boolean isNeighbourOf(TiledNavMesh endMesh) {
		Vector3f dist = worldTranslation.subtract(endMesh.worldTranslation);
		if(FastMath.abs(dist.x)>TERRAIN_SIZE || 
				FastMath.abs(dist.y)>TERRAIN_SIZE)
			return false;
		
		return true;

	}
	

	//the points are on this mesh for sure
	boolean buildNavigationPath(Path NavPath, Vector3f StartPos, Vector3f EndPos) {
		return buildNavigationPath(NavPath, FindClosestCell(StartPos), StartPos, FindClosestCell(EndPos), EndPos);	
	}

	boolean buildNavigationPathToBorder(Path navPath, Cell startCell, Vector3f startPos,
			Vector3f endPos) {
//		if (hasBounds()) {
			// get intersection point of direct route and border lines of tile
			Line2D l = new Line2D(startPos.x, startPos.z, endPos.x, endPos.z);
			Vector2f cross = new Vector2f();
			//FIXME aemhm what if we do not intersect top?!?! 
			LINE_CLASSIFICATION classi = top.Intersection(l, cross);
			
			//check which side we cross
			if(classi != LINE_CLASSIFICATION.SEGMENTS_INTERSECT){
				classi = right.Intersection(l, cross);
				if(classi != LINE_CLASSIFICATION.SEGMENTS_INTERSECT){
					classi = bottom.Intersection(l, cross);
					if(classi != LINE_CLASSIFICATION.SEGMENTS_INTERSECT){
						classi = left.Intersection(l, cross);
						if(classi != LINE_CLASSIFICATION.SEGMENTS_INTERSECT)
							return false;
					}
				}
			}
			//TODO new plan, we have a crossing, ok, now find the closest cell on that border on our side, the cross must be shifted to that cell
			// then go from there to the final destination
			if(cross != null){
				int where = getBoundingSide(cross.x, cross.y);
				//collect all boundig cells on that tiles
				HashSet<Cell> targets = getBorderCells(where);
				//max dist can not be more than the span of a mesh tile
				float max = (TERRAIN_SIZE*TERRAIN_SIZE) +
							(TERRAIN_SIZE*TERRAIN_SIZE) + 0.01f;
				float dist = 0f;
				Cell targetCell = null;
				Vector3f goal = null;
				//loop over them and find the closest one //what if none on that sides, why loop at some which are completely off ???
				for(Cell c : targets){
					//prefere computed cross section over midpoints
					if(c.IsPointInCellCollumn(cross)){
						targetCell = c;
						goal = new Vector3f(cross.x, 0, cross.y);
						c.MapVectorHeightToCell(goal);
						break;
					}
					//all cell midpoints 
					for(Vector3f point : c.m_WallMidpoint){
						//not needed, as this will not work, midpoints will be always on the line
//						//to find the one on the border
//						if(!isPointInTile(point.x, point.z)){
							//and which is closer than the ones before the current
							dist = cross.distanceSquared(point.x, point.z);
							if(dist<max){
								max = dist;
								targetCell = c;
								goal = point;
							}
//						}
					}
				}
				//build path to that midsection point
				if(targetCell != null){
					if(startCell != null)
						return buildNavigationPath(navPath, startCell, startPos, targetCell, goal);
					else
						return buildNavigationPath(navPath, FindClosestCell(startPos), startPos, targetCell, goal);
				}
				else
					return false;
			}
//		}
		return false;
	}

//	public Cell ResolveMotionOnMesh(Vector3f startPos, Cell startCell,
//			Vector3f endPos) {
//		return this.mesh.ResolveMotionOnMesh(startPos, startCell, endPos);
//	}

	
	//FIXME redo debug box border rendering
//	public void bordersDebugRender(com.jme3.scene.Node rootNode) {
//		if(debugRoot == null)
//			debugRoot = new com.jme3.scene.Node("Borderpoints of Mesh "+this.getName());
//		
//		rootNode.detachChild(debugRoot);
//		debugRoot.detachAllChildren();
//		for(Vector3f vec : borderPoints){
//			Box b = new Box(vec,0.8f,0.8f,0.8f);
//			//todo just a color
//	        debugRoot.attachChild(b);
//		}
//		rootNode.attachChild(debugRoot);
//		debugRoot.updateRenderState();
//	}
	@Override
	public String toString(){
		StringBuilder str = new StringBuilder(this.getClass().getSimpleName());
		str.append(" x:").append(worldTranslation.x).append(", z:")
			.append(worldTranslation.z).append(" id:").append(hashCode()).append('\n')
			.append(" worldPos:").append(worldTranslation).append(" extents:").append(TERRAIN_SIZE).append('\n')
			.append(" Borders top/tr/right/rb/bottom/bl/left/lt:"+this.allBorders.get(BORDER_TOP).size()+"/"
			+this.allBorders.get(BORDER_TOP_RIGHT).size()+"/"+this.allBorders.get(BORDER_RIGHT).size()+"/"
			+this.allBorders.get(BORDER_BOTTOM_RIGHT).size()+"/"+this.allBorders.get(BORDER_BOTTOM).size()
			+"/"+this.allBorders.get(BORDER_BOTTOM_LEFT).size()+"/"+this.allBorders.get(BORDER_LEFT).size()
			+"/"+this.allBorders.get(BORDER_TOP_LEFT).size());
		
		return str.toString();
	}

	/**
	 * 
	 * @return a clone of the current position
	 */
	public Vector3f getPosition() {
		return worldTranslation.clone();
	}
	
	/**
	 * All cells of the navmesh as a renderable Geometry
	 * @return Geometry containing all cells
	 */
	public Geometry getDebugMesh(){
		Mesh m = new Mesh();
		m.setMode(Mode.Triangles);
		IntBuffer ib = BufferUtils.createIntBuffer(this.mCellArray.length*3*3);
		FloatBuffer vb = BufferUtils.createFloatBuffer(this.mCellArray.length*3*3);
        vb.rewind();
        int i=0;
        for(Cell c : mCellArray){
        	for(int v= 0;v<3;v++){
        		vb.put(c.m_Vertex[v].x);
        		vb.put(c.m_Vertex[v].y);
        		vb.put(c.m_Vertex[v].z);
        		ib.put(i++);
        	}
        }
		m.setBuffer(Type.Position, 3, vb);
		m.setBuffer(Type.Index, 3, ib);
		m.updateBound();
		
		Geometry g = new Geometry("Debug_NavMesh_"+this.toString(),m);
		g.updateModelBound();
		return g;
	}
	
	/**
	 * Packs all border cells into a renderable mesh
	 * @return Geometry containing the boder cells
	 */
	public Geometry getDebugBorderMesh(){
		Mesh m = new Mesh();
		m.setMode(Mode.Triangles);
		int size = 0;
		for(HashSet<com.l2client.navigation.Cell>  set : allBorders){
			size += set.size();
		}
		IntBuffer ib = BufferUtils.createIntBuffer(size*3*3);
		FloatBuffer vb = BufferUtils.createFloatBuffer(size*3*3);
        vb.rewind();
        int i=0;
        for(HashSet<com.l2client.navigation.Cell>  set : allBorders){
	        for(Cell c : set){
	        	for(int v= 0;v<3;v++){
	        		vb.put(c.m_Vertex[v].x);
	        		vb.put(c.m_Vertex[v].y);
	        		vb.put(c.m_Vertex[v].z);
	        		ib.put(i++);
	        	}
	        }
        }
        log.severe("Debug Borders for:"+this.toString());
        if(i <= 0){
        	log.warning("Navmesh without any bordercells:"+this);
        }
		m.setBuffer(Type.Position, 3, vb);
		m.setBuffer(Type.Index, 3, ib);
		m.updateBound();
		
		Geometry g = new Geometry("Debug_NavBorderMesh_"+this.toString(),m);
		g.updateModelBound();
		return g;
	}
	

	public Geometry getDebugBounds(float y){
		Collection<Geometry> geometries = new ArrayList<Geometry>();
		Line l = new Line(new Vector3f(top.EndPointA().x, y, top.EndPointA().y), 
						  new Vector3f(top.EndPointB().x, y, top.EndPointB().y));
		geometries.add(new Geometry("top", l));

		
		l = new Line(new Vector3f(right.EndPointA().x, y, right.EndPointA().y), 
				  new Vector3f(right.EndPointB().x, y, right.EndPointB().y));
		geometries.add(new Geometry("right", l));
		
		l = new Line(new Vector3f(bottom.EndPointA().x, y, bottom.EndPointA().y), 
				  new Vector3f(bottom.EndPointB().x, y, bottom.EndPointB().y));
		geometries.add(new Geometry("bottom", l));
		
		l = new Line(new Vector3f(left.EndPointA().x, y, left.EndPointA().y), 
				  new Vector3f(left.EndPointB().x, y, left.EndPointB().y));
		geometries.add(new Geometry("left", l));

		Mesh m = new Mesh();
		GeometryBatchFactory.mergeGeometries(geometries, m);
		Geometry g = new Geometry("bounds of "+toString(), m);
		g.updateModelBound();
		return g;

	}
	
	public void setPosition(Vector3f position){
		//if a position has already been set we do this only by the offset if it is really of any value
		Vector3f offset = position.subtract(worldTranslation);
		if(offset.length() > 0.0001f){
			//update all cells
			for(Cell c : mCellArray){
//				c.Initialize(c.m_Vertex[0].add(offset), c.m_Vertex[1].add(offset), c.m_Vertex[2].add(offset));
				//update all centers
				c.m_CenterPoint.addLocal(offset);
				//update all vertices
				for(Vector3f v : c.m_Vertex){
					v.addLocal(offset);
				}
				//update the plane
				c.m_CellPlane.setPlanePoints(c.m_Vertex[0], c.m_Vertex[1], c.m_Vertex[2]);
				//update the wallmidpoints
				for(Vector3f v : c.m_WallMidpoint){
					v.addLocal(offset);
				}
				//update all lines, not in a loop as they are created as references to the same 3 vert2 instances
//				m_Side[SIDE_AB] = new Line2D(Point1, Point2); // line AB
//				m_Side[SIDE_BC] = new Line2D(Point2, Point3); // line BC
//				m_Side[SIDE_CA] = new Line2D(Point3, Point1); // line CA
				c.m_Side[0].EndPointA().addLocal(offset.x, offset.z);//A
				c.m_Side[0].EndPointB().addLocal(offset.x, offset.z);//B
				c.m_Side[1].EndPointB().addLocal(offset.x, offset.z);//C
			}
//			LinkCells();
			//all cells updated besides worldtrans
			worldTranslation = position.clone();
			//uses world trans to create new border lines
			createBounds();
		}
	}
}
