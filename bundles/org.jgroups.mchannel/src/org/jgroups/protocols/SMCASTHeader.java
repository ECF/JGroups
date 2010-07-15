package org.jgroups.protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jgroups.Header;
import org.jgroups.util.Streamable;

public class SMCASTHeader extends Header implements Streamable{
	
	//	CONSTRUCTORS --
	
	public SMCASTHeader() {
		
	}
	
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		/*type = in.readInt();
		if (type==DATA){
			dest = new OLSRNode();
			dest.readExternal(in);
		}*/
	}
	
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
		/*type = in.readByte();
		if (type==DATA){
			dest = new OLSRNode();
			dest.readFrom(in);
			byte[] addr = new byte[4];
			for(int i=0;i<4;i++){
				addr[i]=in.readByte();
			}
			mcastAddress = InetAddress.getByAddress(addr).getHostAddress();
		}*/
	}

	public String toString() {
        return "[SMCAST: <variables> ]";
    }
	
	public void writeExternal(ObjectOutput out) throws IOException {
		/*out.writeInt(type);
		if (type==DATA){
			dest.writeExternal(out);
		}*/
	}

	public void writeTo(DataOutputStream out) throws IOException {
		/*out.writeByte(type);
		if (type==DATA){
			dest.writeTo(out);
			byte[] addr = InetAddress.getByName(mcastAddress).getAddress();
			for(int i=0;i<addr.length;i++){
				out.writeByte(addr[i]);
			}
		}*/
		
	}

	/**
	 * @return the type
	 */
	/*public int getType() {
		return type;
	}*/

	/**
	 * @param type the type to set
	 */
	/*public void setType(int type) {
		this.type = type;
	}*/

	/**
	 * @return the dest
	 */
	/*public OLSRNode getDest() {
		return dest;
	}*/

	/**
	 * @param dest the dest to set
	 */
	/*public void setDest(OLSRNode dest) {
		this.dest = dest;
	}*/

}
