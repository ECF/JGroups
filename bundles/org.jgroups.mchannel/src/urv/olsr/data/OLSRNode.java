package urv.olsr.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.jgroups.Address;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Streamable;

import urv.conf.PropertiesLoader;

/**
 * Object that represents a node in a network based in OLSR protocol.
 * OLSR does not make any assumption about node addresses, other than
 * that each node is assumed to have a unique IP address.
 * 
 * @author Marcel Arrufat Arias
 * @author Raul Gracia Tinedo
 */
public class OLSRNode implements Serializable,Streamable,Externalizable{

	//	CLASS FIELDS --
	
	private InetAddress address;
	//Bandwidth information about each node
	private float bandwithCoefficient;
	private long bwBytesCapacity;
	private long bwMessagesCapacity;
	
	//	CONSTRUCTORS --
	
	public OLSRNode() {}

	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		OLSRNode node = new OLSRNode();
		node.setValue(this.address);
		node.setBandwithCoefficient(this.bandwithCoefficient);
		node.setBwBytesCapacity(this.bwBytesCapacity);
		node.setBwMessagesCapacity(this.bwMessagesCapacity);
		return node;
	}	
	public boolean equals(Object obj){
		OLSRNode node = (OLSRNode)obj;
		return address.equals(node.address);
	}
	/**
	 * Returns a JGroups address with the current InetAddress
	 * @return
	 */
	public Address getJGroupsAddress(){
		return new IpAddress(address,PropertiesLoader.getUnicastPort()); 
	}
	public int hashCode(){
		return address.hashCode();
	}	
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		byte[] b = new byte[4];
		in.read(b, 0, 4);
		this.address=InetAddress.getByAddress(b);
		in.read(b, 0, 4);	//read the bandwidth coefficient, 4 bytes
	    this.bandwithCoefficient = ByteBuffer.wrap(b).getFloat();
	    b = new byte[8];
	    in.read(b, 0, 8);	//read the bytes capacity, 8 bytes
	    this.bwBytesCapacity = ByteBuffer.wrap(b).getLong();
	    in.read(b, 0, 8);	//read the messages capacity, 8 bytes
	    this.bwMessagesCapacity = ByteBuffer.wrap(b).getLong();
	}
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
        byte[] a = new byte[4]; // 4 bytes (IPv4)
        in.readFully(a, 0, 4);
        this.address=InetAddress.getByAddress(a);
        in.readFully(a, 0, 4);	//read the bandwidth coefficient, 4 bytes
        this.bandwithCoefficient = ByteBuffer.wrap(a).getFloat();
        a = new byte[8];
        in.readFully(a, 0, 8);	//read the bytes capacity, 8 bytes
        this.bwBytesCapacity = ByteBuffer.wrap(a).getLong();
        in.readFully(a, 0, 8);	//read the messages capacity, 8 bytes
        this.bwMessagesCapacity = ByteBuffer.wrap(a).getLong();
	}
	public String toString(){
		return address.toString() + " bw_bytes: " + bwBytesCapacity +
			"bw_messages: " + bwMessagesCapacity + " bw_coefficient: " + bandwithCoefficient;
	}
	public void writeExternal(ObjectOutput out) throws IOException {
		out.write(address.getAddress());
		ByteBuffer byteBuffer = ByteBuffer.allocate(4);
		out.write(byteBuffer.putFloat(bandwithCoefficient).array());
		byteBuffer = ByteBuffer.allocate(8);
		out.write(byteBuffer.putLong(bwBytesCapacity).array());
		byteBuffer = ByteBuffer.allocate(8);
		out.write(byteBuffer.putLong(bwMessagesCapacity).array());
	}
	public void writeTo(DataOutputStream out) throws IOException {
        byte[] a = address.getAddress();  // 4 bytes (IPv4)
        out.write(a, 0, a.length);
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byte[] b = byteBuffer.putFloat(bandwithCoefficient).array();
        out.write(b);
        byteBuffer = ByteBuffer.allocate(8);
        b = byteBuffer.putLong(bwBytesCapacity).array();
        out.write(b);
        byteBuffer = ByteBuffer.allocate(8);
        b = byteBuffer.putLong(bwMessagesCapacity).array();
        out.write(b);
	}

	//	PUBLIC METHODS --
	
	public void updateBandwidth (OLSRNode updatedNode){
		this.bandwithCoefficient = updatedNode.getBandwithCoefficient();
		this.bwBytesCapacity = updatedNode.getBwBytesCapacity();
		this.bwMessagesCapacity = updatedNode.getBwMessagesCapacity();
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the address.
	 */
	public InetAddress getAddress() {
		return address;
	}
	public synchronized float getBandwithCoefficient() {
		return bandwithCoefficient;
	}
	public synchronized long getBwBytesCapacity() {
		return bwBytesCapacity;
	}	
	public synchronized long getBwMessagesCapacity() {
		return bwMessagesCapacity;
	}
	public synchronized void setBandwithCoefficient(float bandwithCoefficient) {
		this.bandwithCoefficient = bandwithCoefficient;
	}	
	public synchronized void setBwBytesCapacity(long bwBytesCapacity) {
		this.bwBytesCapacity = bwBytesCapacity;
	}
	public synchronized void setBwMessagesCapacity(long bwMessagesCapacity) {
		this.bwMessagesCapacity = bwMessagesCapacity;
	}
	public void setValue(InetAddress address) {		
		this.address = address;
	}
}