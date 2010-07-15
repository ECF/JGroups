package urv.olsr.mcast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jgroups.Address;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Streamable;

/**
 * Class that represents a multicast address of a group
 * 
 * @author Gerard Paris Aixala
 *
 */
public class MulticastAddress implements Serializable,Streamable,Externalizable{

	//	CLASS FIELDS --
	
	private InetAddress mcastAddress;

	//	CONSTRUCTORS --
	
	public MulticastAddress(){}

	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		MulticastAddress newAddr = new MulticastAddress();
		newAddr.setValue(this.mcastAddress);
		return newAddr;
	}
	public boolean equals(Object obj){
		MulticastAddress addr = (MulticastAddress)obj;
		return mcastAddress.equals(addr.mcastAddress);
	}
	public int hashCode(){
		return mcastAddress.hashCode();
	}
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		byte[] b = new byte[4];
		in.read(b, 0, 4);
		this.mcastAddress=InetAddress.getByAddress(b);
	}
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
		 byte[] a = new byte[4]; // 4 bytes (IPv4)
	     in.readFully(a);
	     this.mcastAddress=InetAddress.getByAddress(a);
	}
	public InetAddress toInetAddress(){
		return mcastAddress;
	}
	public String toString(){
		return mcastAddress.toString();
	}
	public void writeExternal(ObjectOutput out) throws IOException {
		out.write(mcastAddress.getAddress());
	}
	public void writeTo(DataOutputStream out) throws IOException {
		byte[] a = mcastAddress.getAddress();  // 4 bytes (IPv4)
        out.write(a, 0, a.length);
	}
	
	//	ACCESS METHODS --
	
	public void setValue(Address multicastAddress){
		this.mcastAddress = ((IpAddress)multicastAddress).getIpAddress();
	}
	public InetAddress getMcastAddress() {
		return mcastAddress;
	}
	public void setValue(InetAddress multicastAddress){
		this.mcastAddress = multicastAddress;
	}
	public void setValue(String multicastAddress){
		try {
			this.mcastAddress = InetAddress.getByName(multicastAddress);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
}