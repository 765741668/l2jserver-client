package com.l2client.network.game.ClientPackets;

import com.jme3.math.Vector3f;
import com.l2client.model.l2j.ServerValues;

/**
 *        
 *         48
 *         _x  = readD();
        _y  = readD();
        _z  = readD();
        _heading  = readD();
        //UNUSED
        _data  = readD();
 *
 */
//TODO when do we call this?
public class ValidatePosition extends GameClientPacket {

	/**
	 * 
	 * @param pos		position vector 
	 * @param heading	in degree
	 */
	public ValidatePosition(Vector3f pos, float heading){
		writeC(0x30);
		writeD(ServerValues.getServerCoordX(pos.x));
		writeD(ServerValues.getServerCoordY(pos.z));
		writeD(ServerValues.getServerCoordZ(pos.y));
		writeD(ServerValues.getServerHeading(heading));//Heading in L2J is in degree multiplied by 182.044444444
		writeD(0);
//System.out.println("VALIDATE PLAYER: "+ServerValues.getServerCoord(pos.x)+ ","+ServerValues.getServerCoord(pos.z)+","+ServerValues.getServerCoord(pos.y)+" -> "+pos);
	}
}
