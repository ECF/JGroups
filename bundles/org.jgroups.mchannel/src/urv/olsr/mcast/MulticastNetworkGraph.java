package urv.olsr.mcast;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import urv.log.Loggable;
import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.OLSRNode;
import urv.util.graph.Edge;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * @author Gerard Paris Aixala
 *
 */
public class MulticastNetworkGraph implements Loggable, BandwidthUpdatable{
	
	//	CLASS FIELDS --

	private NetworkGraph<OLSRNode,Weight> graph;
	private MulticastGroupsTable multicastGroupsTable;	
	private OLSRNode localNode;	
	private Object lock = new Object();
	private Weight dummyWeight = new Weight();
	
	//	CONSTRUCTORS --
	
	/**
	 * This constructor does not initialize the multicastGroupsTable.
	 *
	 */
	public MulticastNetworkGraph(){
		graph = new NetworkGraph<OLSRNode,Weight>();
	}	
	public MulticastNetworkGraph(MulticastGroupsTable multicastGroupsTable,OLSRNode localNode){
		graph = new NetworkGraph<OLSRNode,Weight>();
		this.multicastGroupsTable = multicastGroupsTable;
		this.localNode = localNode;
	}
	
	//	OVERRIDDEN METHODS --
	
	@Override
	public String toString(){
		StringBuffer buff = new StringBuffer();
		buff.append("MULTICAST_NETWORK_GRAPH["+localNode+"]\n");		
		buff.append(graph.toString());
		return buff.toString();
	}
	@Override
	public void updateBwOf(OLSRNode node) {
		synchronized (lock) {
			if (localNode.equals(node)) localNode.updateBandwidth(node);
			graph.updateBwOf(node);
		}		
	}		
	@Override
	public Object clone(){
		MulticastNetworkGraph newGraph = new MulticastNetworkGraph();				
		newGraph.graph = (NetworkGraph<OLSRNode,Weight>)this.graph.clone();
		newGraph.multicastGroupsTable = (MulticastGroupsTable)this.multicastGroupsTable.clone();
		newGraph.localNode = (OLSRNode)this.localNode.clone();		
		return newGraph;
	}	
	
	//	PUBLIC METHODS --
	
	public void addEdge(OLSRNode node1,OLSRNode node2){
		synchronized(lock){
			if (!graph.areNeighbours(node1, node2)){
				graph.addEdge(node1,node2,dummyWeight);
			}
		}
	}
	public void addEdges(OLSRNode node1,OLSRNode node2){
		synchronized(lock){
			if (!graph.areNeighbours(node1, node2)){
				graph.addEdge(node1,node2,dummyWeight);
			}
			if (!graph.areNeighbours(node2, node1)){
				graph.addEdge(node2,node1,dummyWeight);
			}
		}
	}
	public void addNode(OLSRNode localNode2) {
		graph.addNode(localNode2);	
	}		
	/**
	 * Computes the contracted graph for the specified group
	 */
	public NetworkGraph<OLSRNode,Weight> computeContractedGraph(String mcastAddr){
		
		NetworkGraph<OLSRNode,Weight> newGraph = (NetworkGraph<OLSRNode,Weight>)this.graph.clone();
		newGraph.assignWeightToAllEdges(new Float(1.0f));
		//CHANGED 08-04-21: add local node in case there is only 1 host running
		newGraph.addNode(localNode);		
		Set<OLSRNode> groupNodes = getGroupMembers(mcastAddr);		
		Set<OLSRNode> nodes = new HashSet<OLSRNode>();
		for (Edge e:this.graph.getEdges()){
			if (!nodes.contains(e.getSource())){
				nodes.add((OLSRNode)e.getSource());
			}
			if (!nodes.contains(e.getTarget())){
				nodes.add((OLSRNode)e.getTarget());
			}
		}		
		for(OLSRNode node : nodes){
		// 1. For each node that does not belong to the group...
			if (!groupNodes.contains(node)){			
				// 2. Obtain edges where this node is the src or the target
				LinkedList<Edge> nodeEdges = (LinkedList<Edge>)newGraph.getEdges(node);			
				// 3. Obtain the neighbors of this node
				Set<OLSRNode> neighs = newGraph.getLinkedNodes(node);
				if (neighs==null){
					continue;
				} else { 
					//Do nothing() 					
				}
				// 4. Double loop for the neighbors
				for(OLSRNode ni: neighs){
					for(OLSRNode nj: neighs){			
						// 5. If neighbors are not the same...
						if (!ni.equals(nj)){							
							// 6. Create an edge between them with the weight = sum(edges to node), unless
							// an edge with less than or equal weight already exists
							
							// 6.1 Obtain an edge that links ni to node, and nj to node
							Edge niEdge = newGraph.getEdgeBetween(ni,node);
							Edge njEdge = newGraph.getEdgeBetween(nj,node);							
							if (!niEdge.getWeight().isSet()){
								niEdge.getWeight().setValue(new Float(1.0f));
							}
							if (!njEdge.getWeight().isSet()){
								njEdge.getWeight().setValue(new Float(1.0f));
							}							
							// 6.2 Obtain the weight 
							Weight newWeight = Weight.add(niEdge.getWeight(), njEdge.getWeight());							
							// 6.3 Check the existance of an edge between both nodes
							Edge existingEdge = newGraph.getEdgeBetween(ni,nj);							
							if (existingEdge==null){
								newGraph.addEdge(ni, nj, newWeight);
							} else if (newWeight.compareTo(existingEdge.getWeight())<0){
								newGraph.removeEdges(ni,nj);
								newGraph.addEdge(ni, nj, newWeight);
							}							
						}
					}
				}
				// 7. Remove the edges where this node is the src or the target
				newGraph.removeEdges(node);
			}
		}		
		return newGraph;	
	}	
	/**
	 * This method only copies the reference of the network graph (not the multicastGroupsTable)
	 */
	public void setCopyOfGraph(MulticastNetworkGraph newGraph){	
		// TODO At this moment, the copy is only an update of the references
		synchronized(lock){
			graph = newGraph.graph;
		}
	}
	
	//	ACCESS METHODS --
	
	/**
	 * Returns a set that includes all nodes that have joined the specified
	 * multicast group.
	 * @param mcastAddr The multicast address of the group
	 * @return the set of the nodes of the group
	 */
	public Set<OLSRNode> getGroupMembers(String mcastAddr){
		return multicastGroupsTable.getGroupMembers(mcastAddr);
	}	
	/**
	 * @return the multicastGroupsTable
	 */
	public MulticastGroupsTable getMulticastGroupsTable() {
		return multicastGroupsTable;
	}
}