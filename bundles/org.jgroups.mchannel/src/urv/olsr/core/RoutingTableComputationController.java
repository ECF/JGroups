package urv.olsr.core;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.OLSRPairSet;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.neighbour.NeighborsOfNeighborsSet;
import urv.olsr.data.routing.RoutingTable;
import urv.olsr.data.routing.RoutingTableEntry;
import urv.olsr.data.topology.OLSRNodePair;
import urv.olsr.data.topology.TopologyInformationBaseTable;

/**
 * Each node maintains a routing table which allows it to route data,
   destined for the other nodes in the network.  The routing table is
   based on the information contained in the local link information base
   and the topology set.  Therefore, if any of these sets are changed,
   the routing table is recalculated to update the route information
   about each destination in the network.  The route entries are
   recorded in the routing table in the following format:

         1.  R_dest_addr    R_next_addr    R_dist   R_iface_addr
         2.  R_dest_addr    R_next_addr    R_dist   R_iface_addr
         3.      ,,             ,,           ,,          ,,

   Each entry in the table consists of R_dest_addr, R_next_addr, R_dist,
   and R_iface_addr.  Such entry specifies that the node identified by
   R_dest_addr is estimated to be R_dist hops away from the local node,
   that the symmetric neighbor node with interface address R_next_addr
   is the next hop node in the route to R_dest_addr, and that this
   symmetric neighbor node is reachable through the local interface with
   the address R_iface_addr.  Entries are recorded in the routing table
   for each destination in the network for which a route is known.  All
   the destinations, for which a route is broken or only partially
   known, are not recorded in the table.

   More precisely, the routing table is updated when a change is
   detected in either:

     -    the link set,

     -    the neighbor set,

     -    the 2-hop neighbor set,

     -    the topology set,

     -    the Multiple Interface Association Information Base,

   More precisely, the routing table is recalculated in case of neighbor
   appearance or loss, when a 2-hop tuple is created or removed, when a
   topology tuple is created or removed or when multiple interface
   association information changes.  The update of this routing
   information does not generate or trigger any messages to be
   transmitted, neither in the network, nor in the 1-hop neighborhood.

   To construct the routing table of node X, a shortest path algorithm
   is run on the directed graph containing the arcs X -> Y where Y is
   any symmetric neighbor of X (with Neighbor Type equal to SYM), the
   arcs Y -> Z where Y is a neighbor node with willingness different of
   WILL_NEVER and there exists an entry in the 2-hop Neighbor set with Y
   as N_neighbor_main_addr and Z as N_2hop_addr, and the arcs U -> V,
   where there exists an entry in the topology set with V as T_dest_addr
   and U as T_last_addr.
   
 * @author Marcel Arrufat Arias
 */
public class RoutingTableComputationController {

	//	CLASS FIELDS --
	
	private RoutingTable routingTable;
	private NeighborTable neighborTable;
	private OLSRNode localNode;
	private TopologyInformationBaseTable topologyTable;
	private NeighborsOfNeighborsSet neighborsOfNeighborsSet;	
	// Temporal data structures
	private RoutingTable tmpRoutingTable;
	private OLSRSet tmpSymNeighbors;
	private OLSRPairSet tmpNoNs;
	private TopologyInformationBaseTable tmpTopologyTable;
	
	//	CONSTRUCTORS --
		
	public RoutingTableComputationController(NeighborTable neighborTable,RoutingTable routingTable, 
			TopologyInformationBaseTable topologyTable, NeighborsOfNeighborsSet neighborsOfNeighborsSet, OLSRNode localNode) {		
		this.neighborTable = neighborTable;
		this.routingTable = routingTable;
		this.neighborsOfNeighborsSet = neighborsOfNeighborsSet;
		this.topologyTable = topologyTable;
		this.localNode = localNode;		
		tmpRoutingTable = new RoutingTable(localNode);
	}
	
	//	PUBLIC METHODS --
	
	public void computeNewRoutingTable(){		
		createDataStructures();
		computeRoutingTableAlgorithm();
		copyRoutingTable();
	}
	
	//	PRIVATE METHODS --
	
	/**
	 * Computes a new routing table in order to be able to send 
	 * messages in a efficient way
	 */
	private void computeRoutingTableAlgorithm() {		
		//2. For each SYM Neigh add to the table
		for(OLSRNode destAddr:tmpSymNeighbors){
			//Neighbors are located at one hop
			tmpRoutingTable.addRoutingTableEntry(destAddr,destAddr,1);
		}
		//3a. For each 2-hop neighbor, add entry neighbor<-->2-hop neighbor
		for (OLSRNodePair pair:tmpNoNs){
			OLSRNode neigh = pair.getOriginator();
			OLSRNode non = pair.getAdvertised();
			//The 2-hop neighbor should not be the localNode or a 1-hop neighbor
			if (!non.equals(localNode)&& !tmpSymNeighbors.contains(non)){				
				//If the entry does not exist or there are a more efficient route to arrive at the same destination, add entry
				RoutingTableEntry oldEntry = tmpRoutingTable.getRoutingTableEntry(non);
				if (oldEntry == null){
					tmpRoutingTable.addRoutingTableEntry(non,neigh,2);
				}
			}
		}		
		//3b Process topology table entries		
		int hops=2;		
		boolean entryAdded;
		
		do {
			entryAdded=false;			
			for (OLSRNodePair pair:tmpTopologyTable.keySet()){
				//Advertised is the destAddr
				//Originator is the last hop to get to Advertised				
				OLSRNode destAddr = pair.getAdvertised();
				OLSRNode lastAddr = pair.getOriginator();				
				//If the destAddr does not have an entry in the routing table and
				//lastAddr has one entry whose distance is equal to hops
				if (tmpRoutingTable.getRoutingTableEntry(destAddr)==null && tmpRoutingTable.getRoutingTableEntry(lastAddr)!=null){					
					if (tmpRoutingTable.getRoutingTableEntry(lastAddr).getHops()==hops){
						if (!destAddr.equals(localNode)){
							tmpRoutingTable.addRoutingTableEntry(destAddr,tmpRoutingTable.getRoutingTableEntry(lastAddr).getNextAddr(),hops+1);
							entryAdded=true;								
						}
					}
				}
			}
			//Increase the number of hops
			hops++;
		} while(entryAdded);		
	}
	private void copyRoutingTable() {		 
		routingTable.setCopyOfTable(tmpRoutingTable);
	}
	/**
	 * Empties the used datastructures
	 *
	 */
	private void createDataStructures() {
		tmpSymNeighbors = neighborTable.getCopyOfSymNeighbors();
		tmpNoNs = (OLSRPairSet)neighborsOfNeighborsSet.clone();
		tmpTopologyTable = (TopologyInformationBaseTable)topologyTable.clone();
		//1. All the entries are removed
		tmpRoutingTable.clear();		
		// TODO Should we clone the topologyTable??		
	}
}