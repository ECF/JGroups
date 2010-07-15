package urv.olsr.mcast;

import java.util.HashSet;
import java.util.Set;

import urv.log.Loggable;
import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.OLSRNode;
import urv.util.graph.HashMapSet;

/**
 * This class is used to relate a node with all the multicast groups
 * which it's subscribed
 * 
 * @author Gerard Paris Aixala
 *
 */
public class MulticastGroupsTable implements Loggable, BandwidthUpdatable{
	
	//	CLASS FIELDS --

	private HashMapSet<OLSRNode,MulticastAddress> table = new HashMapSet<OLSRNode,MulticastAddress>();
	private boolean multicastGroupsTableChangedFlag;	
	private Object lock = new Object();
	
	//	CONSTRUCTORS --
	
	public MulticastGroupsTable(){}

	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		MulticastGroupsTable newTable = new MulticastGroupsTable();
		newTable.table = (HashMapSet<OLSRNode,MulticastAddress>)this.table.clone();
		return newTable;
	}
	public String toString(){
		StringBuffer buff = new StringBuffer();
		buff.append("MULTICAST_GROUPS_TABLE["+""+"]\n");		
		buff.append(table.toString());
		return buff.toString();
	}	
	@Override
	public void updateBwOf(OLSRNode node) {
		synchronized (lock) {
			for (OLSRNode currentNode : table.keySet()){
				if (currentNode.equals(node)) currentNode.updateBandwidth(node);
			}
			setMulticastGroupsTableChangedFlag(true);
		}
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Obtains a new HashSet with cloned copies of the members of this multicast group
	 * @param mcastAddr
	 * @return
	 */
	public Set<OLSRNode> getGroupMembers(String mcastAddr) {
		MulticastAddress mcastAddress = new MulticastAddress();
		mcastAddress.setValue(mcastAddr);
		
		synchronized(lock){	
			Set<OLSRNode> groupMembers = new HashSet<OLSRNode>();
			for (OLSRNode node : table.keySet()){
				if (table.get(node).contains(mcastAddress)){
					groupMembers.add((OLSRNode)node.clone());
				}
			}
			return groupMembers;
		}
	}
	/**
	 * Returns the multicast addresses of the groups joined by the node
	 * @param localNode
	 * @return
	 */
	public Set<MulticastAddress> getJoinedMulticastGroups(OLSRNode node) {
		return table.getSet(node);
	}
	public void registerMulticastGroup(OLSRNode node, String mcastAddrName) {
		synchronized(lock){
			MulticastAddress mcastAddr = new MulticastAddress();
			mcastAddr.setValue(mcastAddrName);
			table.addToSet(node, mcastAddr);
			setMulticastGroupsTableChangedFlag(true);
		}
	}
	public void unregisterMulticastGroup(OLSRNode node, String mcastAddrName) {
		synchronized(lock){
			MulticastAddress mcastAddr = new MulticastAddress();
			mcastAddr.setValue(mcastAddrName);
			table.removeFromSet(node,mcastAddr);
			setMulticastGroupsTableChangedFlag(true);
		}
	}
	public void updateMulticastGroups(OLSRNode originatorNode, Set<MulticastAddress> joinedGroups) {
		synchronized(lock){
			Set<MulticastAddress> oldGroups = table.get(originatorNode);
			if (oldGroups==null || !oldGroups.equals(joinedGroups)){ 
				// TODO Check this comparation is really working!!!				
				table.remove(originatorNode);
				for(MulticastAddress mcastAddr : joinedGroups){
					table.addToSet(originatorNode, mcastAddr);
				}				
				setMulticastGroupsTableChangedFlag(true);
			}
		}
	}	
	
	//	ACCESS METHODS --
	
	/**
	 * @return the multicastGroupsTableChangedFlag
	 */
	public boolean isMulticastGroupsTableChangedFlag() {
		return multicastGroupsTableChangedFlag;
	}
	/**
	 * @param multicastGroupsTableChangedFlag the multicastGroupsTableChangedFlag to set
	 */
	public void setMulticastGroupsTableChangedFlag(
			boolean multicastGroupsTableChangedFlag) {
		this.multicastGroupsTableChangedFlag = multicastGroupsTableChangedFlag;
	}
}