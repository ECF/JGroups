package urv.olsr.data.routing;

import java.util.Hashtable;

import urv.log.Loggable;
import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.OLSRNode;

/**
 * Contains routing information. It defines which is the next intermediate 
 * node A that should receive the message when we want to deliver 
 * a message to node B 
 * 
 * @author Marcel Arrufat Arias
 */
public class RoutingTable implements Loggable, BandwidthUpdatable{

	//	CLASS FIELDS --
	
	private Hashtable<OLSRNode,RoutingTableEntry> routingTable;
	private Object lock = new Object();
	private OLSRNode localNode;
	
	//	CONSTRUCTORS --
	
	public RoutingTable(OLSRNode localNode) {
		routingTable = new Hashtable<OLSRNode,RoutingTableEntry>();
		this.localNode = localNode;
	}
	
	//	OVERRIDDEN METHODS --
	
	public String toString(){
		StringBuffer buff = new StringBuffer();
		buff.append("ROUTING_TABLE["+localNode+"]\n");
		synchronized (lock) {
			for(OLSRNode node:this.routingTable.keySet()){
				RoutingTableEntry entry = this.routingTable.get(node);
				buff.append("\tDest:["+entry.getDestAddr()+"]-NextHop ["+entry.getNextAddr()+"] hops = "+entry.getHops()+
						" bw_coefficient of last hop = "+entry.getNextAddr().getBandwithCoefficient()+"\n");
			}
		}		
		return buff.toString();		
	}
	@Override
	public void updateBwOf(OLSRNode node) {
		synchronized (lock) {			
			for (OLSRNode keyNode : routingTable.keySet()){
				if (keyNode.equals(node)) keyNode.updateBandwidth(node);
			}
			RoutingTableEntry entryForOriginator = getRoutingTableEntry(node);
			if (entryForOriginator!=null){
				entryForOriginator.getDestAddr().updateBandwidth(node);
			}
		}
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds a new entry to the routing table
	 * @param destAddr
	 * @param nextAddr
	 * @param hops
	 */
	public void addRoutingTableEntry(OLSRNode destAddr,OLSRNode nextAddr, int hops){
		synchronized (lock) {
			routingTable.put(destAddr,new RoutingTableEntry(destAddr,nextAddr,hops));
		}
	}
	/**
	 * Empties the routing table
	 *
	 */
	public void clear(){	
		routingTable.clear();
	}	
	/**
	 * Obtains an entry from the routing table
	 * @param node
	 * @return
	 */
	public RoutingTableEntry getRoutingTableEntry(OLSRNode node){		
		synchronized (lock) {
			return routingTable.get(node);
		}		
	}
	/**
	 * Copies an existing routing table, which is calculated in another
	 * thread, to the current table
	 * @param tmpRoutingTable
	 */
	public void setCopyOfTable(RoutingTable tmpRoutingTable) {
		Hashtable<OLSRNode,RoutingTableEntry> newRoutingTable = new Hashtable<OLSRNode,RoutingTableEntry>();
		synchronized (lock) {			
			for (OLSRNode node:tmpRoutingTable.routingTable.keySet()){
				newRoutingTable.put((OLSRNode)node.clone(),(RoutingTableEntry)tmpRoutingTable.routingTable.get(node).clone());
			}
			//Change the reference of the object
			this.routingTable = newRoutingTable;
		}		
	}		
}