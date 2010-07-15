package urv.omolsr.util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import urv.util.graph.GraphException;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * This class is used to compute the minimum spanning tree
 * for a network graph, that represents a multicast group. We
 * find this tree using the Prim's Algorithm.
 * 
 * @author Gerard Paris Aixala
 *
 */
public class GraphUtils<N,W> {

	//	CLASS FIELDS --
	
	final Weight dummyWeight = new Weight();
	
	//	PUBLIC METHODS --
	
	/**
	 * Computes a minimum spanning tree for the specified graph and source node
	 * @param graph A network graph
	 * @param source The source node to compute the tree from
	 * @return the computed spanning tree
	 */
	public synchronized NetworkGraph<N,W> computeMinimumSpanningTree(NetworkGraph<N, W> graph, N source){
		/* 
		 * Prim's Algorithm: http://en.wikipedia.org/wiki/Prim's_algorithm
		 * 
	     * Input: A connected weighted graph G(V,E)
	     * Initialize: V' = {x}, where x is an arbitrary node from V, E'= {}
	     * repeat until V'=V:
	          o Choose edge (u,v) from E with minimal weight such that u is in V' and v is not in V' (if there are multiple edges with the same weight, choose arbitrarily)
	          o Add v to V', add (u,v) to E'
	     * Output: G(V',E') is the minimal spanning tree
		 */
		NetworkGraph<N,W> mst = new NetworkGraph<N, W>();
		
		Hashtable<N,Integer> distances = new Hashtable<N,Integer>();
		Hashtable<N,Boolean> visited  = new Hashtable<N,Boolean>();
		
		String graphStr = graph.toString();
		// Initialize distances & visited
		Set<N> nodeList = graph.getNodeList();
		for (N n : nodeList){
			graphStr+="\nIterated on node "+n;
			distances.put(n, new Integer(Integer.MAX_VALUE));
			visited.put(n, new Boolean(Boolean.FALSE));
		}		
		// Source node distance
		distances.put(source, new Integer(0));

		try {			
			for (int i=0;i<distances.size();i++){				
				// get closest vertex to visit from the visited nodes
				final N next = minVertex(distances, visited);
				visited.put(next, new Boolean(true));
				
				final Set<N> neighbors = getNonVisitedNeighbors(graph, next,visited);
				for(N nj : neighbors){
					// Get weight between new selected node and all neighbours
					int d = 0;
					if (graph.getEdgeBetween(next, nj)!=null){
						d = graph.getEdgeBetween(next, nj).getWeight().getValue().intValue();
					} else {
						d = graph.getEdgeBetween(nj, next).getWeight().getValue().intValue();
					}					
					// Update distances from neighbours to new selected node
					if (distances.get(nj).intValue() > d) {
						distances.put(nj, d);
						mst.removeEdgesWithTarget(nj);
						mst.addEdge(next, nj, dummyWeight);					
					}		
				}					
			}
		} catch (Exception e) {
			System.err.println("This error has already implemented a solution for this error");
			e.printStackTrace();			
			//return an empty graph or a graph with all nodes at 1-hop 
			return getMstAfterError(source,nodeList);
		}
		return mst;
	}
	
	//	PRIVATE METHODS --
	
	/**
	 * Returns an graph when an error has ocurred in MST computation.
	 * Tipically will return a graph with all nodes located at one hop
	 * from this node
	 * @param nodeList 
	 * @param source 
	 * @return
	 */
	private NetworkGraph<N, W> getMstAfterError(N source, Set<N> nodeList) {
		NetworkGraph<N,W> mst = new NetworkGraph<N, W>();
		for (N node:nodeList){
			if (!source.equals(node)){
				mst.addEdge(source,node,dummyWeight);
			}
		}
		return mst;
	}
	/**
	 * Returns a list of non-visited neighbors of the specified node
	 * @param graph The reference graph
	 * @param node 
	 * @param visited
	 * @return
	 */
	private synchronized Set<N> getNonVisitedNeighbors(NetworkGraph<N, W> graph, N node, Hashtable<N, Boolean> visited) {
		Set<N> neighbors = (HashSet<N>) graph.getLinkedNodes(node);		
		// Remove visited neighbors
		Iterator<N> it = neighbors.iterator();
		while(it.hasNext()){
			N n = it.next();
			if (((Boolean)visited.get(n)).booleanValue()==true){
				it.remove();
			}
		}
		return neighbors;
	}	
	/**
	 * Returns the non-visited node with the minimum distance in the table.
	 * @param distances
	 * @param visitedNodes
	 * @return 
	 * @throws Exception 
	 */
	private synchronized N minVertex(Hashtable<N,Integer> distances, Hashtable<N,Boolean> visitedNodes) throws GraphException {
		int minDistance = Integer.MAX_VALUE;
		N minNode = null;
		
		for (N node : distances.keySet()){
			boolean visited = ((Boolean)visitedNodes.get(node)).booleanValue();
			int distance = ((Integer)distances.get(node)).intValue();
			if (visited==false && distance < minDistance){
				minNode = node;
				minDistance = distance;
			}
		}
		if (minNode==null){
			//When this is happenning, we are working with a non connected graph
			//Raise exception and terminate computation
			throw new GraphException("Graph error exception. We are working with a non connected graph.");
		}
		return minNode;
	}	
}