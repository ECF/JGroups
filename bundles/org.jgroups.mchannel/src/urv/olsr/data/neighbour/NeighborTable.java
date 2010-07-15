package urv.olsr.data.neighbour;

import urv.log.Loggable;
import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.ExpiringEntryTable;
import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.MprSet;
import urv.olsr.data.mpr.OLSRPairSet;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.topology.OLSRNodePair;
import urv.olsr.message.HelloMessage;
import urv.util.graph.HashMapSet;

/**
 * Table which contains information about neighbors of the current
 * node and status with all neighbors. A list of neighbors of 
 * neighbors (NoN) is also available for each entry
 * This table contains information about the MPRSelectors of the node 
 * 
 * Entries will be removed when timeout expires or when receiving anew
 * HELLO_MESSAGE with LinkCode == NOT_NEIGH
 * 
 * @author Marcel Arrufat Arias
 */
public class NeighborTable extends ExpiringEntryTable<OLSRNode,NeighborTableEntry> implements Loggable, BandwidthUpdatable{

	//	CLASS FIELDS --
	
	private OLSRNode localNode;
	private boolean recomputeMprFlag;
	private boolean neighborTableChangedFlag;
	private NeighborsOfNeighborsSet neighborsOfNeighborsSet;
	private OLSRPairSet tmpNoNSet;
	
	//	CONSTRUCTORS --
	
	/**
	 * Creates a new table (with entries that may expire) for
	 * storing all information about neighbors
	 * @param localNode 
	 * @param neighborsOfNeighborsSet 
	 *
	 */
	public NeighborTable(OLSRNode localNode, NeighborsOfNeighborsSet neighborsOfNeighborsSet) {
		this.localNode = localNode;
		this.neighborsOfNeighborsSet = neighborsOfNeighborsSet;
		//Temporary storage of NoNs
		tmpNoNSet = new OLSRPairSet();
	}	
	
	//	OVERRIDDEN METHODS --
	
	public String toString(){
		return "NEIGHBOR_TABLE["+localNode+"]"+"\n"+super.toString();
	}
	public void updateBwOf(OLSRNode originatorNode) {
		synchronized (super.getLock()){
			//Update the node in the keySet
			if (getEntry(originatorNode)!=null){
				getEntry(originatorNode).getNeighbor().updateBandwidth(originatorNode);
				//Update all the references to the node to update
				neighborsOfNeighborsSet.updateBwOf(originatorNode);
				//notify changes are performed
				setNeighborTableChangedFlag(true);
				setRecomputeMprFlag(true);
			}
		}
	}
	public int hashCode(){
		return localNode.hashCode();
	}
	@Override
	public void onTableChange() {
		//Every time a changed has been done, recompute NoNs
		//and notify changes are performed		
		tmpNoNSet.clear();
		//3. Compute NoN list: for each entry, look for each NoN and 
		//add it to the list if not exists
		synchronized (super.getLock()) {
			for (OLSRNode node:this.keySet()){
				OLSRSet nonList = this.getEntry(node).getNeighborsOfNeighbors();
				for (OLSRNode non:nonList){
					//TODO: change! it should check if it contains an OLSRpair
					OLSRNodePair newPair = new OLSRNodePair(node,non);
					if (!tmpNoNSet.contains(newPair) && !non.equals(localNode))
						tmpNoNSet.add(newPair);	
				}
			}
			neighborsOfNeighborsSet.setCopyOfSet(tmpNoNSet);
		}

		//notify changes are performed
		setNeighborTableChangedFlag(true);
		setRecomputeMprFlag(true);
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds a new entry to the table
	 * @param node
	 * @param entry
	 */	
	public void addNeighborEntryWithTimestamp(OLSRNode node,NeighborTableEntry entry,int expiringTime){
		super.addEntryWithTimestamp(node,entry,expiringTime);
	}
	/**
	 * Returns a new hello message from the information stored in the
	 * neighbor table
	 * @return HelloMessage
	 */
	public HelloMessage createHelloMessage(){
		HashMapSet<LinkCode,OLSRNode> map = new HashMapSet<LinkCode,OLSRNode>();
		synchronized (super.getLock()) {
			for (OLSRNode node:this.keySet()){
				NeighborTableEntry entry = getEntry(node);
				
				map.addToSet((LinkCode)(entry.getLinkCode().clone()),(OLSRNode)node.clone()); 
			}
		}
		HelloMessage hm = new HelloMessage(map);
		return hm;
	}
	/**
	 * Checks if the node has an entry inside the Neighbor Table
	 * @param node
	 */
	public boolean entryExists(OLSRNode node){
		return super.getEntry(node)!=null;
	}
	/**
	 * Returns a copy of a set containing all the neighbors of
	 * the node
	 * @return OLSRSet
	 */
	public OLSRSet getCopyOfNeighbors(){
		OLSRSet copy = new OLSRSet();
		synchronized (super.getLock()) {
			for(OLSRNode node:this.keySet()){
				copy.add((OLSRNode)node.clone());
			}
		}
		return copy;
	}
	/**
	 * Returns a copy of a set containing all the neighbors of
	 * the node
	 * @return OLSRSet
	 */
	public OLSRSet getCopyOfSymNeighbors(){
		OLSRSet copy = new OLSRSet();
		synchronized (super.getLock()) {
			for(OLSRNode node:this.keySet()){
				LinkCode status = this.getEntry(node).getLinkCode();
				if (status.getNeighborType()==LinkCode.MPR_NEIGH || status.getNeighborType()==LinkCode.SYM_NEIGH)
					copy.add((OLSRNode)node.clone());
			}
		}
		return copy;
	}
	/**
	 * Returns true if the specified node is in the symmetric 1-hop neighborhood
	 * of the local node
	 * @param node
	 * @return if is a symmetric neighbor
	 */
	public boolean isSymmetricNeighbor(OLSRNode node){
		NeighborTableEntry entry = super.getEntry(node);
		synchronized (super.getLock()) {
			if (entry!=null){
				if (entry.getLinkCode().getNeighborType()==LinkCode.MPR_NEIGH ||
						entry.getLinkCode().getNeighborType()==LinkCode.SYM_NEIGH){
					return true;
				}
			}
		}
		return false;
	}
	/**
	 * This method is invoked when changes are performed in the tmpMprSet
	 * we need to update the link status in the Neighbor Table
	 * @param mprSet2 
	 */
	public void onMPRSetChange(MprSet mprSet2) {
		//Iterate over all the nodes in the neighborhood
		synchronized (super.getLock()) {
		
			for (OLSRNode node:super.keySet()){
				NeighborTableEntry entry = super.getEntry(node);
				//if the node is selected as MPR
				if (mprSet2.contains(node)){
					entry.getLinkCode().setNeighborType(LinkCode.MPR_NEIGH);
				}
				else{
					//Check if the node role has changed and now is not mpr
					if (entry.getLinkCode().getNeighborType()==LinkCode.MPR_NEIGH){
						entry.getLinkCode().setNeighborType(LinkCode.SYM_NEIGH);	
					}
					
				}
			}
		}
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the localNode.
	 */
	public OLSRNode getLocalNode() {
		return localNode;
	}	
	/**
	 * @return Returns the neighborTableChangedFlag.
	 */
	public boolean isNeighborTableChangedFlag() {
		return neighborTableChangedFlag;
	}	
	/**
	 * @return Returns the recomputeMPR.
	 */
	public boolean isRecomputeMprFlag() {
		return recomputeMprFlag;
	}
	/**
	 * This Flag is set to true when a new neighbor or 2-hop neighbor is detected 
	 * @param neighborTableChangedFlag The neighborTableChangedFlag to set.
	 */
	public synchronized void setNeighborTableChangedFlag(boolean neighborTableChanged) {
		this.neighborTableChangedFlag = neighborTableChanged;
	}
	/**
	 * @param recomputeMPR The recomputeMPR to set.
	 */
	public synchronized void  setRecomputeMprFlag(boolean recomputeMPR) {
		this.recomputeMprFlag = recomputeMPR;
	}
}