package urv.omolsr.data;

import java.io.Serializable;
import java.util.Set;

import urv.olsr.data.OLSRNode;
import urv.omolsr.util.GraphUtils;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * @author Gerard Paris Aixala
 *
 */
public class OMOLSRNetworkGraph implements Serializable {

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	
	public static GraphUtils<OLSRNode,Weight> graphUtils = new GraphUtils<OLSRNode,Weight>();
	private NetworkGraph<OLSRNode,Weight> graph; 
	
	//	CONSTRUCTORS --
	
	public OMOLSRNetworkGraph(NetworkGraph<OLSRNode,Weight> graph){
		this.graph = graph;
	}	
	
	//	PUBLIC METHODS --
	
	public NetworkGraph<OLSRNode,Weight> computeMinimumSpanningTree(OLSRNode localNode){
		return graphUtils.computeMinimumSpanningTree(graph,localNode);
	}	
	public Set<OLSRNode> getGroupNodes(){
		return graph.getNodeList();
	}	
	/**
	 * This method retrieves the lowest credit capacity in bytes of the
	 * nodes in the route to the targetNode (included)
	 */
	public long getLowestBandwithInBytesOfRouteTo(OLSRNode localNode, OLSRNode targetNode) {
		return findRecursivelyLowestBandwidthInBytesOfRouteTo(localNode, targetNode, 0)[1];
	}
	public NetworkGraph<OLSRNode,Weight> getNetworkGraphCopy(){
		return (NetworkGraph<OLSRNode,Weight>)graph.clone();
	}
	/**
	 * Returns the neighbors of the node. Since this is a contracted graph
	 * i.e, only members of the group are present in this graph, virtual
	 * neighbors of a node are the same as "regular" neighbors
	 * @param node
	 * @return
	 */
	public Set<OLSRNode> getVirtualNeighbors(OLSRNode node) {
		return graph.getLinkedNodes(node);
	}	
	
	//	OVERRIDDEN METHODS --
	
	public String toString(){
		return graph.toString();
	}
	
	//	PRIVATE METHODS --
	
	/**
	 * Find the shortest route to the target node, retrieving the number of hops
	 * and the lowest credit
	 * 
	 * @param localNode
	 * @param targetNode
	 * @param hops
	 * @return pair [hops][lowestCredit]
	 */
	private long[] findRecursivelyLowestBandwidthInBytesOfRouteTo(OLSRNode localNode, OLSRNode targetNode, int hops) {
		/*
		 * Algorithm to find the lowest credit capacity in a route:
		 * 1. If localNode == targetNode, retrieve the localNode capacity
		 * 2. If the targetNode is a 1-hop neighbor of localNode, return the targetNode capacity
		 * 3. Otherwise:
		 * 		a) For each neighbor of localNode find its subtree
		 * 		b) If the subtree contains the target node:
		 * 			b1.- If subtree's head is a 1-hop neighbor, retrieve the lowest capacity
		 * 			b2.- Else, repeat step a) for subtree's head
		 * Note that we retrieve the lowest credit of the shortest route, because is possible
		 * that some subtrees can contain the target node. In case that two neighbors are able
		 * to arrive at the destination (with the same number of hops), select the best node depending
		 * on the bandwidth coefficient of the nodes.
		 */
		//If we are the localNode, return our capacity
		if (localNode.equals(targetNode)) return new long[] {hops, localNode.getBwBytesCapacity()};
		//If we are searching into the route (hops>0) fill the result variable with the lowest
		//credit between the target and me (the final case)
		long minCreditBetweenNeighborAndTarget = (hops>0)? 
				((targetNode.getBwBytesCapacity()<localNode.getBwBytesCapacity())?
					targetNode.getBwBytesCapacity() : localNode.getBwBytesCapacity()) : 
				targetNode.getBwBytesCapacity();
		long[] result = new long[] {++hops, minCreditBetweenNeighborAndTarget};		
		//Compute the MST for localNode
		NetworkGraph<OLSRNode, Weight> mst = graphUtils.computeMinimumSpanningTree(getNetworkGraphCopy() ,localNode);
		long[] branchResult = null;
		if (!mst.areNeighbours(localNode, targetNode)){
			branchResult = new long[2];
			for (OLSRNode scaningNode : mst.getNeighbours(localNode)){
				//If the target node doesn't belong to this tree, exit
				if (!mst.getSubtree(scaningNode).contains(targetNode)) continue;
				// Update the lowest capacity, if necessary
				long[] tmpNeighborBranchResult = findRecursivelyLowestBandwidthInBytesOfRouteTo(scaningNode, targetNode, hops);
				if (branchResult[1] == 0){
					branchResult = tmpNeighborBranchResult;
				}
			}
		}
		//If we have had to search into branches, update the hop count
		if (branchResult!=null){
			result[0] = branchResult[0];
			//If the result of the search is a credit lower than current one, update it
			if (branchResult[1]<result[1]) result[1] = branchResult[1];
		}
		return result;
	}
}