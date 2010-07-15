package urv.bwcalc;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This class encapsulates the bandwidth information of a node
 * in a certain moment. This information is passed up through the
 * protocol stack to manage and take advantage of this information
 * 
 * @author Marc Espelt
 *
 */
public class BwData implements Serializable {

	//	CLASS FELDS --
	
	private static final long serialVersionUID = 4787274017657405408L;	
	private long max_incoming_packets = 0;
    private long max_incoming_bytes = 0;
    
    //	OVERRIDDEN METHODS --
    
    public String toString(){
    	String s = "";
    	for (Field f : this.getClass().getDeclaredFields()){
    		if ((f.getModifiers() & Modifier.STATIC) == 0)
    			try {s += f.getName() + "=" + f.getLong(this) + " : "; } catch (Exception e) {}
    	}
    	return "[" + s.substring(0, s.length()-3) + "]";
    }
    
    //	ACCESS METHODS --
    
    public long getMaxIncomingBytes() {
		return max_incoming_bytes;
	}
    public long getMaxIncomingPackets() {
		return max_incoming_packets;
	}
    public void setMaxIncomingBytes(long max_incoming_bytes) {
		this.max_incoming_bytes = max_incoming_bytes;
	}
    public void setMaxIncomingPackets(long max_incoming_packets) {
		this.max_incoming_packets = max_incoming_packets;
	}
}