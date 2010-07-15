package urv.olsr.data.duplicate;

import urv.olsr.data.ExpiringEntryTable;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * Upon receiving a basic packet, a node examines each of the "message
   headers".  Based on the value of the "Message Type" field, the node
   can determine the fate of the message.  A node may receive the same
   message several times.  Thus, to avoid re-processing of some messages
   which were already received and processed, each node maintains a
   Duplicate Set.  In this set, the node records information about the
   most recently received messages where duplicate processing of a
   message is to be avoided.  For such a message, a node records a
   "Duplicate Tuple" (D_addr, D_seq_num, D_retransmitted, D_iface_list,
   D_time), where D_addr is the originator address of the message,
   D_seq_num is the message sequence number of the message,
   D_retransmitted is a boolean indicating whether the message has been
   already retransmitted, D_iface_list is a list of the addresses of the
   interfaces on which the message has been received and D_time
   specifies the time at which a tuple expires and *MUST* be removed.

   In a node, the set of Duplicate Tuples are denoted the "Duplicate
   set".

   In this section, the term "Originator Address" will be used for the
   main address of the node which sent the message.  The term "Sender
   Interface Address" will be used for the sender address (given in the
   IP header of the packet containing the message) of the interface
   which sent the message.  The term "Receiving Interface Address" will
   be used for the address of the interface of the node which received
   the message.
   
 * @author Gerard Paris Aixala
 *
 */
public class DuplicateTable extends ExpiringEntryTable<DuplicateTableEntry,Boolean>{

	//	CONSTANTS --
	
	public static final int DUP_HOLD_TIME = 30000; //30 sec
	
	//	CLASS FIELDS --
	
	private OLSRNode localNode;
	
	//	CONSTRUCTORS --
	
	public DuplicateTable(OLSRNode localNode) {
		this.localNode = localNode;
	}

	//	OVERRIDDEN METHODS --
	
	@Override
	public void onTableChange() {
		//This method does nothing
	}

	public String toString(){
		return "DUPLICATE_SET["+localNode+"]"+"\n"+super.toString();
	}
	
	//	PUBLIC METHODS --
	
	public boolean containsSameAddrSeq(OLSRNode originator, SequenceNumber messageSequenceNumber) {
		if (super.containsKey(new DuplicateTableEntry(originator,messageSequenceNumber))){
			return true;
		}
		return false;
	}
	public boolean isRetransmitted(OLSRNode originator, SequenceNumber seqNumber) {
		DuplicateTableEntry tmp = new DuplicateTableEntry(originator,seqNumber);
		if (containsKey(tmp)){
			return ((Boolean)getEntry(tmp)).booleanValue();
		}
		return false;
	}
}