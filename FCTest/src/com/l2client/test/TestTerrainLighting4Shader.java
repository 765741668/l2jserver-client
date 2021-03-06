package com.l2client.test;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingBox;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.util.SkyFactory;

/**
 * Uses the terrain's lighting texture with normal maps and lights.
 *
 * @author bowens
 */
public class TestTerrainLighting4Shader extends SimpleApplication {

    private TerrainQuad terrain;
    Material matTerrain;
    protected BitmapText hintText;
    PointLight pl;
    Geometry lightMdl;
    private float grassScale = 4;
    private float dirtScale = 4;
    private float rockScale = 4;
    private float brickScale = 4;

    public static void main(String[] args) {
        TestTerrainLighting4Shader app = new TestTerrainLighting4Shader();
        app.start();
    }

    @Override
    public void initialize() {
        super.initialize();

        loadHintText();
    }

    @Override
    public void simpleInitApp() {
        setupKeys();

        // First, we load up our textures and the heightmap texture for the terrain

        // TERRAIN TEXTURE material
        matTerrain = assetManager.loadMaterial("tile/120_177/120_177.j3m");
        
//        matTerrain = new Material(assetManager, "com/l2client/materials/TerrainLighting4.j3md");
//
//        // ALPHA map (for splat textures)
//        matTerrain.setTexture("AlphaMap", assetManager.loadTexture("Textures/Terrain/splat/alpha1.png"));
//
//        // HEIGHTMAP image (for the terrain heightmap)
        Texture heightMapImage = assetManager.loadTexture("Textures/Terrain/splat/mountains512.png");
//
//        // GRASS texture
//        Texture grass = assetManager.loadTexture("Textures/Terrain/splat/grass.jpg");
//        grass.setWrap(WrapMode.Repeat);
//        matTerrain.setTexture("DiffuseMap_0", grass);
//        matTerrain.setFloat("DiffuseMap_0_scale", grassScale);
//
//        // DIRT texture
//        Texture dirt = assetManager.loadTexture("Textures/Terrain/splat/dirt.jpg");
//        dirt.setWrap(WrapMode.Repeat);
//        matTerrain.setTexture("DiffuseMap_1", dirt);
//        matTerrain.setFloat("DiffuseMap_1_scale", dirtScale);
//
//        // ROCK texture
//        Texture rock = assetManager.loadTexture("Textures/Terrain/splat/road.jpg");
//        rock.setWrap(WrapMode.Repeat);
//        matTerrain.setTexture("DiffuseMap_2", rock);
//        matTerrain.setFloat("DiffuseMap_2_scale", rockScale);
//
//        // BRICK texture
//        Texture brick = assetManager.loadTexture("Textures/Terrain/BrickWall/BrickWall.jpg");
//        brick.setWrap(WrapMode.Repeat);
//        matTerrain.setTexture("DiffuseMap_3", brick);
//        matTerrain.setFloat("DiffuseMap_3_scale", brickScale);
//
//        Texture normalMap0 = assetManager.loadTexture("Textures/Terrain/splat/grass_normal.jpg");
//        normalMap0.setWrap(WrapMode.Repeat);
//        Texture normalMap1 = assetManager.loadTexture("Textures/Terrain/splat/dirt_normal.png");
//        normalMap1.setWrap(WrapMode.Repeat);
//        Texture normalMap2 = assetManager.loadTexture("Textures/Terrain/splat/road_normal.png");
//        normalMap2.setWrap(WrapMode.Repeat);
//        matTerrain.setTexture("NormalMap_0", normalMap0);
//        matTerrain.setTexture("NormalMap_1", normalMap1);
//        matTerrain.setTexture("NormalMap_2", normalMap2);
//        matTerrain.setTexture("NormalMap_3", normalMap2);


        createSky();

        // CREATE HEIGHTMAP
        AbstractHeightMap heightmap = null;
        try {
            //heightmap = new HillHeightMap(1025, 1000, 50, 100, (byte) 3);

            heightmap = new ImageBasedHeightMap(heightMapImage.getImage(), 1f);
            heightmap.load();

        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Here we create the actual terrain. The tiles will be 65x65, and the total size of the
         * terrain will be 513x513. It uses the heightmap we created to generate the height values.
         */
        /**
         * Optimal terrain patch size is 65 (64x64).
         * The total size is up to you. At 1025 it ran fine for me (200+FPS), however at
         * size=2049, it got really slow. But that is a jump from 2 million to 8 million triangles...
         */
        terrain = new TerrainQuad("terrain", 65, 513, heightmap.getHeightMap());//, new LodPerspectiveCalculatorFactory(getCamera(), 4)); // add this in to see it use entropy for LOD calculations
        TerrainLodControl control = new TerrainLodControl(terrain, getCamera());
        terrain.addControl(control);
        terrain.setMaterial(matTerrain);
        terrain.setModelBound(new BoundingBox());
        terrain.updateModelBound();
        terrain.setLocalTranslation(0, -100, 0);
        terrain.setLocalScale(1f, 1f, 1f);
        rootNode.attachChild(terrain);

        DirectionalLight light = new DirectionalLight();
        light.setDirection((new Vector3f(-0.5f, -1f, -0.5f)).normalize());
        rootNode.addLight(light);

        AmbientLight ambLight = new AmbientLight();
        ambLight.setColor(new ColorRGBA(1f, 1f, 0.8f, 0.2f));
        rootNode.addLight(ambLight);

        cam.setLocation(new Vector3f(0, 10, -10));
        cam.lookAtDirection(new Vector3f(0, -1.5f, -1).normalizeLocal(), Vector3f.UNIT_Y);
        cam.setFrustumFar(10000f);
    }

    public void loadHintText() {
        hintText = new BitmapText(guiFont, false);
        hintText.setSize(guiFont.getCharSet().getRenderedSize());
        hintText.setLocalTranslation(0, getCamera().getHeight(), 0);
        hintText.setText("Hit T to switch to wireframe,  P to switch to tri-planar texturing");
        guiNode.attachChild(hintText);
    }

    private void setupKeys() {
        flyCam.setMoveSpeed(50);
    }

    private void createSky() {
        Texture west = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_west.jpg");
        Texture east = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_east.jpg");
        Texture north = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_north.jpg");
        Texture south = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_south.jpg");
        Texture up = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_up.jpg");
        Texture down = assetManager.loadTexture("Textures/Sky/Lagoon/lagoon_down.jpg");

        Spatial sky = SkyFactory.createSky(assetManager, west, east, north, south, up, down);
        rootNode.attachChild(sky);
    }
}
