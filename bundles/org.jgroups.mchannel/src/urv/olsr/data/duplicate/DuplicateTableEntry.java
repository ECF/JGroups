package urv.olsr.data.duplicate;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * Key of the DuplicateTable. It must contain information 
 * about the most recently received messages where duplicate
 * processing is to be avoided.
 * 
 * @author Gerard Paris Aixala
 */
public class DuplicateTableEntry implements Cloneable{

	//	CLASS FIELDS --
	
	private OLSRNode originatorAddr; // The originator address of the message
	private SequenceNumber msgSeqNum; // The message sequence number of the message

	//	CONSTRUCTORS --
	
	public DuplicateTableEntry(OLSRNode originatorAddr, SequenceNumber msgSeqNum) {		
		this.originatorAddr = originatorAddr;
		this.msgSeqNum = msgSeqNum;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		return new DuplicateTableEntry((OLSRNode)this.originatorAddr.clone(),(SequenceNumber)this.msgSeqNum.clone());
	}
	/**
	 * Checks whether two entries are equal
	 */
	public boolean equals(Object obj){
		DuplicateTableEntry entry = (DuplicateTableEntry)obj;
		return originatorAddr.equals(entry.originatorAddr) && msgSeqNum.equals(entry.msgSeqNum);
	}	
	public int hashCode(){
		return originatorAddr.hashCode()*65535+msgSeqNum.hashCode();
	}
	public String toString(){
		return "["+originatorAddr+"("+msgSeqNum+")]"; 
	}
}