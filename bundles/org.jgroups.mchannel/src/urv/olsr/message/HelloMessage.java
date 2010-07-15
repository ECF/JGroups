package urv.olsr.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;

import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.util.graph.HashMapSet;

/**
 * A common mechanism is employed for populating the local link
   information base and the neighborhood information base, namely
   periodic exchange of HELLO messages.
   
 * @author Gerard Paris Aixala
 *
 */
public class HelloMessage implements OLSRMessage/*Serializable,*/{

	//	CLASS FIELDS --
	
	private HashMapSet<LinkCode,OLSRNode> map = new HashMapSet<LinkCode,OLSRNode>();
	
	//	CONSTRUCTORS --
	
	public HelloMessage(){}	
	public HelloMessage(HashMapSet<LinkCode,OLSRNode> map){
		this.map = map; 
	}
	
	//	OVERRIDDEN METHODS --
	
	public void readFrom(DataInputStream in) throws IOException, 
			IllegalAccessException, InstantiationException {
		int mapSize = in.readByte();	// Added by URV to the standard implementation
		for (int i=0;i<mapSize;i++){
			LinkCode linkCode = new LinkCode();
			linkCode.readFrom(in);			
			byte size = in.readByte();
			for (int j=0;j<size;j++){
				OLSRNode node = new OLSRNode();
				node.readFrom(in);
				map.addToSet(linkCode, node);
			}
		}		
	}	
	/**
	 * Returns a String representation of this HELLO message
	 */
	public String toString(){
		StringBuffer buf = new StringBuffer();
		boolean more = false;		
		for (LinkCode linkCode : map.keySet()){
			buf.append(linkCode+" [");
			HashSet<OLSRNode> set = map.getSet(linkCode);
			more = false;
			for (OLSRNode node : set){
				if (more) buf.append(", ");
				buf.append(node);
				more = true;
			}
			buf.append("]\n");
		}
		return buf.toString();
	}
	public void writeTo(DataOutputStream out) throws IOException {		
		out.writeByte(map.size());	// Added by URV to the standard implementation
		
		for (LinkCode linkCode : map.keySet()){
			linkCode.writeTo(out);
			HashSet<OLSRNode> list = map.getSet(linkCode);
			
			out.writeByte(list.size());
			for (OLSRNode node : list){
				node.writeTo(out);
			}
		}
	}		
	
	//	ACCESS METHODS --
	
	public HashMapSet<LinkCode,OLSRNode> getMessageInfo(){
		return map;
	}
}