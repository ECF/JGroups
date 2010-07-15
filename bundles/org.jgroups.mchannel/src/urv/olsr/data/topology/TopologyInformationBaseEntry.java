package urv.olsr.data.topology;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * This class is the data unit to store information about the
 * network topology (through the TC_MESSAGES) into the 
 * TopologyInformationBaseTable class
 * 
 * @author Marcel Arrufat Arias
 */
public class TopologyInformationBaseEntry {

	//	CLASS FIELDS --
	
	private OLSRNode originator;
	private OLSRNode advertisedNode;
	private SequenceNumber seqNum;
	
	//	CONSTRUCTORS --
	
	/**
	 * @param originator
	 * @param advertisedNode
	 * @param seqNum
	 */
	public TopologyInformationBaseEntry(OLSRNode originator, OLSRNode advertisedNode, SequenceNumber seqNum) {
		this.originator = originator;
		this.advertisedNode = advertisedNode;
		this.seqNum = seqNum;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){		
		return new TopologyInformationBaseEntry((OLSRNode)originator.clone(),(OLSRNode)advertisedNode.clone(),(SequenceNumber)seqNum.clone());
	}
	public String toString(){		
		return seqNum.toString();
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the advertisedNode.
	 */
	public OLSRNode getAdvertisedNode() {
		return advertisedNode;
	}
	/**
	 * @return Returns the originator.
	 */
	public OLSRNode getOriginator() {
		return originator;
	}	
	/**
	 * @return Returns the seqNum.
	 */
	public SequenceNumber getSeqNum() {
		return seqNum;
	}	
}