package urv.olsr.data.topology;

import urv.log.Loggable;
import urv.olsr.data.ExpiringEntryTable;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * This class contains information about the received TC_Messages
 * There exists one entry per originator of the TC_Message. The entries
 * contain a list of: advertised neighbors, seq. number and validity time
 * 
 * Each node in the network maintains topology information about the
   network.  This information is acquired from TC-messages and is used
   for routing table calculations.

   Thus, for each destination in the network, at least one "Topology
   Tuple" (T_dest_addr, T_last_addr, T_seq, T_time) is recorded.
   T_dest_addr is the main address of a node, which may be reached in
   one hop from the node with the main address T_last_addr.  Typically,
   T_last_addr is a MPR of T_dest_addr.  T_seq is a sequence number, and
   T_time specifies the time at which this tuple expires and *MUST* be
   removed.
   In a node, the set of Topology Tuples are denoted the "Topology Set".
   
 * @author Marcel Arrufat Arias
 */
public class TopologyInformationBaseTable extends ExpiringEntryTable<OLSRNodePair,TopologyInformationBaseEntry> implements Loggable{

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	private boolean topologyTableChangedFlag;
	private OLSRNode localNode;
	
	//	CONSTRUCTORS --
	
	/** 
	 * @param expiringTime
	 */
	public TopologyInformationBaseTable(OLSRNode localNode) {
		this.localNode = localNode;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		//Get the lock and copy all the info
		TopologyInformationBaseTable topologyTable = new TopologyInformationBaseTable(localNode);		
		synchronized (super.getLock()) {
			//Copy all the entries
			for (OLSRNodePair pair :this.keySet()){
				topologyTable.addEntryWithTimestamp(pair,getEntry(pair),0);
			}
		}		
		return topologyTable;
	}
	public int hashCode(){
		return localNode.hashCode();
	}
	@Override
	public void onTableChange() {
		setTopologyTableChangedFlag(true);
	}
	public String toString(){
		StringBuffer buff = new StringBuffer();
		buff.append("TOPOLOGY_TABLE["+localNode+"]\n");
		if (this.keySet().isEmpty()) {
			buff.append("--empty--\n");
		} 
		else {
			synchronized (super.getLock()) {
				for (OLSRNodePair pair : this.keySet()) {
	
					buff.append("\t[" + pair + "] : ansn=" + this.getEntry(pair) + " (t="+super.get(pair)+")" + "\n");
				}
			}
		}
		return buff.toString();
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds a new entry to the table
	 * @param node
	 * @param entry
	 */
	public void addTopologyInformationBaseEntry(OLSRNode originator,OLSRNode advertisedNode,SequenceNumber seqNum,int expiringTime){
		OLSRNodePair pair = new OLSRNodePair(originator,advertisedNode);
		super.addEntryWithTimestamp(pair,new TopologyInformationBaseEntry(originator,advertisedNode,seqNum),expiringTime);
		setTopologyTableChangedFlag(true);
	}
	/**
	 * Removes all entries of the table from the given originator
	 * @param originator
	 */
	public void removeOldEntriesForOriginator(OLSRNode originator){
		synchronized (super.getLock()) {
			for (OLSRNodePair pair:this.keySet()){
				if (pair.getOriginator().equals(originator)){
					super.removeEntry(pair);
					setTopologyTableChangedFlag(true);
				}
			}
		}
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @param topologyTableChangedFlag The topologyTableChangedFlag to set.
	 */
	public synchronized void setTopologyTableChangedFlag(boolean topologyTableChangedFlag) {
		this.topologyTableChangedFlag = topologyTableChangedFlag;
	}
	/**
	 * @return Returns the topologyTableChangedFlag.
	 */
	public boolean isTopologyTableChangedFlag() {
		return topologyTableChangedFlag;
	}
}