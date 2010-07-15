package urv.olsr.mcast;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.topology.OLSRNodePair;
import urv.olsr.data.topology.TopologyInformationBaseTable;

/**
 * This class contains a graph representation of the nodes neighborhood and
 * their joined multicast groups.
 * 
 * @author Gerard Paris Aixala
 *
 */
public class MulticastNetworkGraphComputationController{

	//	CLASS FIELDS --
	
	private MulticastNetworkGraph multicastNetworkGraph; //Includes multicastGroupTable
	private OLSRNode localNode;
	private NeighborTable neighborTable;
	private TopologyInformationBaseTable topologyTable;	
	// Temporal data structures
	private MulticastNetworkGraph tmpGraph;
	private OLSRSet tmpSymNeighbors;
	private TopologyInformationBaseTable tmpTopologyTable;	
	
	//	CONSTRUCTORS --
	
	public MulticastNetworkGraphComputationController(MulticastNetworkGraph multicastNetworkGraph,
			NeighborTable neighborTable,TopologyInformationBaseTable topologyTable, OLSRNode localNode){
		this.multicastNetworkGraph = multicastNetworkGraph;
		this.neighborTable = neighborTable;
		this.topologyTable = topologyTable;
		this.localNode = localNode;		
	}	
	
	//	PUBLIC METHODS --
	
	public void computeNewMulticastNetworkGraph(){
		createDataStructures();
		populateGraph();
		copyGraph();		
	}
	public void copyGraph(){
		multicastNetworkGraph.setCopyOfGraph(tmpGraph);
	}	
	public void createDataStructures(){		
		tmpGraph = new MulticastNetworkGraph(); // Only the network graph is initialized		
		tmpSymNeighbors = neighborTable.getCopyOfSymNeighbors();
		tmpTopologyTable = (TopologyInformationBaseTable) topologyTable.clone();		
	}
	public void populateGraph(){		
		//Add local node
		tmpGraph.addNode(localNode);		
		//Adding symmetric neighbors and NoN
		for(OLSRNode node:tmpSymNeighbors){
			//Add an edge between the source and each neighbor (2, since the graph is directed)
			tmpGraph.addEdges(localNode,node);			
			//For every neighbor, add NoNs
			OLSRSet list = (OLSRSet)neighborTable.getEntry(node).getNeighborsOfNeighbors().clone();
			for (OLSRNode tmpNeighbour:list){
				if (!tmpNeighbour.equals(localNode) && !tmpSymNeighbors.contains(tmpNeighbour)){
					tmpGraph.addEdges(node,tmpNeighbour);
				}
			}
		}		
		// Adding edges from topologyTable
		for (OLSRNodePair pair:tmpTopologyTable.keySet()){
			// With topology information, only an edge is added (directed graph)
			tmpGraph.addEdge(pair.getOriginator(),pair.getAdvertised());
		}
	}
}