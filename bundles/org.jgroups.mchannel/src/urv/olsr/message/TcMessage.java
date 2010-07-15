package urv.olsr.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.mcast.MulticastAddress;

/**
 * A TC message is sent by a node in the network to declare a set of
   links, called advertised link set which MUST include at least the
   links to all nodes of its MPR Selector set, i.e., the neighbors which
   have selected the sender node as a MPR.

   If, for some reason, it is required to distribute redundant TC
   information, refer to section 15.

   The sequence number (ANSN) associated with the advertised neighbor
   set is also sent with the list.  The ANSN number MUST be incremented
   when links are removed from the advertised neighbor set; the ANSN
   number SHOULD be incremented when links are added to the advertised
   neighbor set.
   
 * @author Gerard Paris Aixala
 *
 */
public class TcMessage implements OLSRMessage,Serializable{

	//	CLASS FIELDS --

	private SequenceNumber ansn;
	private OLSRSet advertisedNeighbors = new OLSRSet();
	//Added to spread information about the joined multicast groups
	private Set<MulticastAddress> joinedMulticastGroups = new HashSet<MulticastAddress>();

	//	CONSTRUCTORS --
	
	public TcMessage(){}
	
	public TcMessage(SequenceNumber ansn, OLSRSet advertisedNeighbours){
		this.ansn = ansn;
		this.advertisedNeighbors = advertisedNeighbours;
	}
	
	//	OVERRIDDEN METHODS --
	
	public void readFrom(DataInputStream in) throws IOException,
			IllegalAccessException, InstantiationException {		
		ansn = new SequenceNumber(0);
		ansn.readFrom(in);
		int listSize = in.readByte(); // Added by URV to the standard implementation
		for (int i=0;i<listSize;i++){
			OLSRNode node = new OLSRNode();
			node.readFrom(in);
			advertisedNeighbors.add(node);
		}
		//Joined Multicast Groups
		int setSize = in.readByte();
		for (int i=0;i<setSize;i++){
			MulticastAddress mcastAddr = new MulticastAddress();
			mcastAddr.readFrom(in);
			joinedMulticastGroups.add(mcastAddr);
		}
	}
	public String toString(){
		StringBuffer buf = new StringBuffer();

		buf.append("ANSN="+ansn);
		buf.append(" [");
		boolean more = false;
		for (OLSRNode node : advertisedNeighbors){
			if (more) buf.append(", ");
			buf.append(node);
			more = true;
		}
		buf.append("]\n");
		return buf.toString();
	}
	public void writeTo(DataOutputStream out) throws IOException {		
		ansn.writeTo(out);
		out.writeByte(advertisedNeighbors.size()); // Added by URV to the standard implementation
		for (OLSRNode node : advertisedNeighbors){
			node.writeTo(out);
		}
		// Joined Multicast Groups
		out.writeByte(joinedMulticastGroups.size());
		for (MulticastAddress mcastAddr : joinedMulticastGroups){
			mcastAddr.writeTo(out);
		}
	}
	
	//	ACCESS METHODS --
	
	public void setJoinedMulticastGroups(Set<MulticastAddress> joinedMulticastGroups){
		this.joinedMulticastGroups = joinedMulticastGroups;
	}
	/**
	 * @return Returns the advertisedNeighbors.
	 */
	public OLSRSet getAdvertisedNeighbors() {
		return advertisedNeighbors;
	}
	/**
	 * @return Returns the ansn.
	 */
	public SequenceNumber getAnsn() {
		return ansn;
	}
	/**
	 * @return the joinedMulticastGroups
	 */
	public Set<MulticastAddress> getJoinedMulticastGroups() {
		return joinedMulticastGroups;
	}
}