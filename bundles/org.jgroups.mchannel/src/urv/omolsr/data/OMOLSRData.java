package urv.omolsr.data;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import urv.olsr.data.OLSRNode;
import urv.util.graph.HashMapSet;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * This class handles all the data related to the construction of the multicast
 * overlay in the local node. It maintains a list of virtual neighbours for 
 * each multicast group, as well as link state table for each group. OMCastData 
 * also provides methods to obtain the headers of the messages that must be forwarded.  
 * 
 * @author Gerard Paris Aixala
 * @author Marcel Arrufat Arias
 *
 */
public class OMOLSRData{

	// CLASS FIELDS --
	
	private static final int VALIDITY_TIME = 10000; // 10 sec
	private OMOLSRNetworkGraph omolsrNetworkGraph = null;	
	private NetworkGraph<OLSRNode,Weight> mstNetworkGraph = null;
	private TemporalNodeTable temporalNodes = new TemporalNodeTable();
	private Object temp = new Object();	
	private OLSRNode localNode;
	protected final Log log = LogFactory.getLog(this.getClass());
	
	//	CONSTRUCTORS --
	
	public OMOLSRData(OLSRNode localNode){
		this.localNode = localNode;
		NetworkGraph<OLSRNode,Weight> graph = new NetworkGraph<OLSRNode,Weight>();
		graph.addNode(localNode);
		omolsrNetworkGraph = new OMOLSRNetworkGraph(graph);		
		/* ***** ADDED NEW ***** */
		new Thread(){
			public void run(){
				long timeBefore = System.currentTimeMillis();
				long timeNow;
				long diff;				
				while(true){
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					timeNow = System.currentTimeMillis();
					diff = timeNow-timeBefore;
					timeBefore = timeNow;					
					temporalNodes.decreaseTimeAndProcessEntries(diff);
				}
			}
		}.start();
		/* ***** ADDED NEW ***** */		
	}	
	
	//	OVERRIDDEN METHODS --
	
	public String toString(){
		StringBuffer buf = new StringBuffer();
		if (omolsrNetworkGraph!=null){
			buf.append("OMOLSR NETWORK GRAPH: \n"+omolsrNetworkGraph.toString());
		}
		if (mstNetworkGraph!=null){
			buf.append("MST NETWORK GRAPH: \n"+mstNetworkGraph.toString());
		}
		return buf.toString();		
	}
	
	//	PUBLIC METHODS --
	
	public void computeMST(){
		mstNetworkGraph = omolsrNetworkGraph.computeMinimumSpanningTree(localNode);
	}	
	public Set<OLSRNode> getGroupNodes(){
		return omolsrNetworkGraph.getGroupNodes();
	}	
	/**
	 * Returns the header that must be set in each message when forwarding a multicast message
	 * IMPORTANT: now all nodes receive exactly the same header (broadcast reminiscence), although 
	 * it should only receive
	 * @param virtualNeighborsToForwardSet
	 * @param nonVirtualNeighborsToForwardSet
	 * @return
	 */
	public HashMapSet<OLSRNode, OLSRNode> getHeadersForForwardingNodes(Set<OLSRNode> virtualNeighborsToForwardSet, Set<OLSRNode> nonVirtualNeighborsToForwardSet){

		HashMapSet<OLSRNode,OLSRNode> forwardingTable = new HashMapSet<OLSRNode,OLSRNode>();
		
		for (OLSRNode vnToForwardMessage:virtualNeighborsToForwardSet){			
			//Get all the "children" of the virtualNeighbor in the network graph
			//i.e, get the subtree under the selected virtualNeighbor and check if inside this subtree
			//we can find nodes which should still receive the message
			
			//In other words, get the nodes in subtree of the node that we send the message to
			Set<OLSRNode> nonVntoForwardSet = getNonVirtualNeighborsInSubtree(nonVirtualNeighborsToForwardSet,vnToForwardMessage);
			//Store info into headers (forwarding table)
			for (OLSRNode nonVntoForward:nonVntoForwardSet){				
				forwardingTable.addToSet(vnToForwardMessage,nonVntoForward);
			}			
		}		
		return forwardingTable;

	}	
	//We will now store info in the headers
	//For each neighbor that we have that is in the list of nodes which should receive the message, add info to headers
	/* END NEW OMOLSR */
	/**
	 * Obtains the headers to be sent into a multicast message
	 * @param localOLSRNode
	 * @return 
	 */
	public HashMapSet<OLSRNode, OLSRNode> getHeadersForSource(OLSRNode localOLSRNode) {
		HashMapSet<OLSRNode, OLSRNode> headers = new HashMapSet<OLSRNode, OLSRNode>();		
		//Get virtual neighbors
		Set<OLSRNode> virtualNeighbors = getMstNetworkGraph().getLinkedNodes(localOLSRNode);
		//For each virtual neighbor, get subtree and add it to the list for this virtual neighbor
		for (OLSRNode virtualNeighbor:virtualNeighbors){
			headers.putSet(virtualNeighbor,getMstNetworkGraph().getSubtree(virtualNeighbor));
		}		
		return headers;
	}	
	/**
	 * Returns a set of node which are not virtualneighbors of the given node and also 
	 * exist in the given list
	 * @param localOLSRNode
	 * @param nodeSet
	 * @return
	 */
	public Set<OLSRNode> getNonVirtualNeighborsExistingInSet(OLSRNode localOLSRNode, HashSet<OLSRNode> nodeSet) {
		Set<OLSRNode> neighborSet = omolsrNetworkGraph.getVirtualNeighbors(localOLSRNode);
		//Store the matching nodes in here
		Set<OLSRNode> resultSet = new HashSet<OLSRNode>();
		for (OLSRNode node:nodeSet){
			if (!neighborSet.contains(node)){
				resultSet.add(node);
			}
		}		
		return resultSet;
	}	
	/**
	 * Gets the subtree under the direct destination node and returns the nodes
	 * which are present in both subtree and nonVirtualNeighbors sets
	 * @param nonVirtualNeighborsToForwardSet
	 * @param vnToForwardMessage
	 * @return
	 */
	public Set<OLSRNode> getNonVirtualNeighborsInSubtree(Set<OLSRNode> nonVirtualNeighborsToForwardSet, OLSRNode directDestinationNode) {
		Set<OLSRNode> subtreeNodes = getMstNetworkGraph().getSubtree(directDestinationNode);
		Set<OLSRNode> nonVirtualNeighborsInSubtreeSet = new HashSet<OLSRNode>();
		for (OLSRNode nodeToForward:nonVirtualNeighborsToForwardSet){
			//Add the node to the set if 
			if (subtreeNodes.contains(nodeToForward)){
				nonVirtualNeighborsInSubtreeSet.add(nodeToForward);
			}
		}
		return nonVirtualNeighborsInSubtreeSet;
	}
	public Set<OLSRNode> getTemporalNodes(){
		Set<OLSRNode> retVal = new HashSet<OLSRNode>();
		synchronized(temp){
			for (OLSRNode n:temporalNodes.keySet()){
				retVal.add(n);
			}
		}
		return retVal;
	}
	public Set<OLSRNode> getVirtualNeighbors(OLSRNode node){		
		return omolsrNetworkGraph.getVirtualNeighbors(node);		
	}
	/**
	 * Returns a set of node which are virtualneighbors of the given node and also 
	 * exist in the given list
	 * @param localOLSRNode
	 * @param nodeSet
	 * @return
	 */
	public Set<OLSRNode> getVirtualNeighborsExistingInSet(OLSRNode localOLSRNode, HashSet<OLSRNode> nodeSet) {
		Set<OLSRNode> neighborSet = omolsrNetworkGraph.getVirtualNeighbors(localOLSRNode);
		//Store the matching nodes in here
		Set<OLSRNode> resultSet = new HashSet<OLSRNode>();
		for (OLSRNode node:nodeSet){
			if (neighborSet.contains(node)){
				resultSet.add(node);
			}
		}		
		return resultSet;
	}
	public void updateOMOLSRNetworkGraph(OMOLSRNetworkGraph omolsrNetworkGraph) {		
		// NEW: Added to avoid problems with non-initialized graphs
		if (omolsrNetworkGraph.getVirtualNeighbors(localNode)==null){
			NetworkGraph<OLSRNode,Weight> graph = new NetworkGraph<OLSRNode,Weight>();
			graph.addNode(localNode);
			this.omolsrNetworkGraph = new OMOLSRNetworkGraph(graph);
		}else{			
			/* ************* ADDED NEW ************** */
			Set<OLSRNode> oldNodeSet = this.omolsrNetworkGraph.getGroupNodes();
			Set<OLSRNode> newNodeSet = omolsrNetworkGraph.getGroupNodes();
			
			synchronized(temp){
				// Removing nodes that are in the new set again
				Iterator it = temporalNodes.keySet().iterator();
				while(it.hasNext()){
					OLSRNode n = (OLSRNode)it.next();
					if (newNodeSet.contains(n)){
						it.remove();
					}
				}
				// Adding nodes that were in the old set but not in the new one
				for (OLSRNode n:oldNodeSet){
					if (!newNodeSet.contains(n)){
						temporalNodes.addEntryWithTimestamp(n, new Object(), VALIDITY_TIME);
					}
				}
			}
			/* ************************************** */			
			this.omolsrNetworkGraph = omolsrNetworkGraph;
		}		
	}	
	
	//	PRIVATE METHODS --
	
	private NetworkGraph<OLSRNode, Weight> getMstNetworkGraph() {
		if (mstNetworkGraph==null)
			computeMST();
		return mstNetworkGraph;
	}
}