package org.jgroups.protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jgroups.Header;
import org.jgroups.util.Streamable;

/**
 * Bandwidth Calculator protocol Header.
 * The communication interface between the protocol and the other layers.
 * 
 * @author Marc Espelt
 */
public class BwCalcHeader extends Header implements Streamable {

	//	CONSTANTS --
	
	public static final int MAX_INCOMING_BANDWIDTH = 1;
	public static final int MAX_INCOMING_MESSAGES = 2;
	
	//	CLASS FIELDS --
	
	private int type = MAX_INCOMING_BANDWIDTH | MAX_INCOMING_MESSAGES;
	
	//	CONSTRUCTORS --
	
	public BwCalcHeader(){
		super();
		this.type = MAX_INCOMING_BANDWIDTH | MAX_INCOMING_MESSAGES;
	}
	
	//	OVERRIDDEN METHODS --
	
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type=in.readByte();
    }
    public String toString() {
		return "[BwCalcHeader]";
    }	
	public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(type);
    }
	public void writeTo(DataOutputStream out) throws IOException {
        out.writeByte(type);
    }
    public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
        type=in.readByte();
    }
    
    //	ACCESS METHODS --
    
	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}
    /**
	 * @param type the type to set
	 */
	public void setType(int type) {
		this.type = type;
	}
}