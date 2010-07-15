package urv.olsr.core;

import java.util.Hashtable;
import java.util.Set;

import urv.log.Log;
import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.MprSet;
import urv.olsr.data.mpr.OLSRPairSet;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.neighbour.NeighborsOfNeighborsSet;
import urv.olsr.data.topology.OLSRNodePair;
import urv.olsr.util.Util;
import urv.util.graph.HashMapSet;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * Computes a new set of MPR with the information stored in the
 * neighbor table.
 * 
 * The MPR set MUST be calculated by a node in such a way that it,
   through the neighbors in the MPR-set, can reach all symmetric strict
   2-hop neighbors.  (Notice that a node, a, which is a direct neighbor
   of another node, b, is not also a strict 2-hop neighbor of node b).
   This means that the union of the symmetric 1-hop neighborhoods of the
   MPR nodes contains the symmetric strict 2-hop neighborhood.  MPR set
   recalculation should occur when changes are detected in the symmetric
   neighborhood or in the symmetric strict 2-hop neighborhood.

   MPRs are computed per interface, the union of the MPR sets of each
   interface make up the MPR set for the node.

   While it is not essential that the MPR set is minimal, it is
   essential that all strict 2-hop neighbors can be reached through the
   selected MPR nodes.  A node SHOULD select an MPR set such that any
   strict 2-hop neighbor is covered by at least one MPR node.  Keeping
   the MPR set small ensures that the overhead of the protocol is kept
   at a minimum.

   The MPR set can coincide with the entire symmetric neighbor set.
   This could be the case at network initialization (and will correspond
   to classic link-state routing).
   
 * @author Marcel Arrufat Arias
 */
public class MprComputationController {
	
	//	CLASS FIELDS --

	private NeighborTable neighborTable;
	private NeighborsOfNeighborsSet neighborsOfNeighborsSet;
	private MprSet mprSet;
	//Additional fields needed for computation
	private OLSRSet tmpNeighbors_N;
	private OLSRSet tmpNeighOfNeigh_N2;
	private OLSRNode localNode;
	private Hashtable<OLSRNode, Integer> neighborsDegree;
	private NetworkGraph<OLSRNode,Weight> graph;
	private Weight dummyWeight;
	private OLSRSet tmpMprSet;
	private HashMapSet<Integer,OLSRNode> reachabilityTable;	
	private Log log = Log.getInstance();

	//	CONSTRUCTORS --
	
	public MprComputationController(NeighborTable neighborTable, NeighborsOfNeighborsSet neighborsOfNeighborsSet, 
			MprSet mprSet, OLSRNode localNode) {
		this.neighborTable = neighborTable;
		this.neighborsOfNeighborsSet = neighborsOfNeighborsSet;
		this.mprSet = mprSet;
		this.localNode = localNode;		
		this.tmpNeighbors_N = new OLSRSet();
		this.tmpNeighOfNeigh_N2 = new OLSRSet();
		this.neighborsDegree = new Hashtable<OLSRNode,Integer>();
		this.graph = new NetworkGraph<OLSRNode,Weight>();
		this.dummyWeight=new Weight();
		this.dummyWeight.setValue(new Float(1));
		this.tmpMprSet = new OLSRSet();
		this.reachabilityTable = new HashMapSet<Integer,OLSRNode>();
	}	
	/**
	 * Compute a new MPR set
	 *
	 */
	public synchronized boolean computeNewMprSet(){
		try {			
			createDataStructures();	
			computeMPRSelectionAlgorithm();
			//Store the information in the public mprSet
			mprSet.setCopyOfSet(tmpMprSet);
			return true;			
		} catch (Exception e) {			
			e.printStackTrace();
			return false;
		}
	}
	
	//	ACCESS METHODS --
	
	/**
	 * Returns the reference to the last computed MPRSet
	 * @return
	 */
	public MprSet getMprSet(){
		return this.mprSet;
	}

	//	PRIVATE METHODS --
	
	/**
	 * Once the information is stored in the data structures decides 
	 * which nodes will be selected to become MPR of the local node
	 * @throws Exception 
	 */
	private void computeMPRSelectionAlgorithm() throws Exception{
		
		//Look for nodes in NoN which only have 1 neighbor, and this neighbor
		//is a neighbor of the local node. That is, look for nodes in N2 which are 
		//accessible just by one node in N
		
		//We will erase the 2-hop nodes which are accessible by a MPR 
		Set<OLSRNode>nonAccessedNon = Util.copyNodeSet(tmpNeighOfNeigh_N2);		
		for(OLSRNode non:tmpNeighOfNeigh_N2){
			//Get neighbors of the node, and check Number of neighbors
			Set<OLSRNode> list = graph.getNeighbours(non);
			if (list==null){
				System.out.println("************************ \nNode "+localNode+" could not find neighbors for node "+non);
				System.out.println("************************ \nGraph is "+graph);
				return;
			}
			int numNeighbours = list.size();
			if (numNeighbours==0){
				log.fatal("Error on adding edges to graph. Will now exit");
			}
			else if (numNeighbours==1){
				OLSRNode node = list.iterator().next();
				if (tmpNeighbors_N.contains(node)){
					//This neighbor of us allows us access to a 2-hop neighbor which is isolated
					tmpMprSet.add(node);
					//The 2-hop neighbor is now accessible, so remove it from the list 
					nonAccessedNon.remove(non);
				}
			}
		}
		//Now, while there are still nodes in N2 (NoNset)
		while (!nonAccessedNon.isEmpty()){
			//compute reachability for each neighbor
			int maxReachability = 0;
			reachabilityTable.clear();
			for(OLSRNode node:tmpNeighbors_N){
				Set<OLSRNode> nonList = graph.getNeighbours(node);
				//For each NoN of the current node, check if they have still to be accessed
				int reachability=0;				
				for(OLSRNode non:nonList){
					if (nonAccessedNon.contains(non)){
						reachability++;
						if (reachability>maxReachability){
							maxReachability=reachability;
						}							
					}
				}
				reachabilityTable.addToSet(new Integer(reachability),node);
			}			
			if (maxReachability==0){
				//Something went wrong or we do not have information on the tables
				return;
			}
			//Find the node with maximum reachability
			Set<OLSRNode> nodeList = reachabilityTable.get(new Integer(maxReachability));
			int maxDegree=0;
			OLSRNode nodeSelectedNewMPR = null;
			for (OLSRNode node:nodeList){				
				if (neighborsDegree.get(node)==null) 
					throw new Exception("Error: Possible data inconsistency");
				int degree = neighborsDegree.get(node).intValue();
				if (degree>maxDegree){
					maxDegree=degree;
					nodeSelectedNewMPR = (OLSRNode)node;
				}
			}
			//TODO: if nodeSelected=null, error or break loop
			tmpMprSet.add(nodeSelectedNewMPR);
			//Remove the nodes from N2 (nonAccessedNon) that the selectedMPR gives access to 
			OLSRSet non = neighborTable.getEntry(nodeSelectedNewMPR).getNeighborsOfNeighbors();
			for (OLSRNode node:non){
				if (nonAccessedNon.contains(node)){					
					nonAccessedNon.remove(node);
				}
			}
		}
	}
	/**
	 * Creates all needed data structures for MPRs selection 
	 *
	 */
	private void createDataStructures() {		 
		//A1.Initialize neighbor set
		tmpNeighbors_N=neighborTable.getCopyOfSymNeighbors();
		//A2.Initialize NoN set. Conditions:
		//Exclude the local node
		//Exclude local node's symm. neighbours
		tmpNeighOfNeigh_N2.clear();
		//Empty temporal MprSet
		tmpMprSet.clear();
		//Empty reachability table
		reachabilityTable.clear();
		for (OLSRNodePair nodePair:(OLSRPairSet)neighborsOfNeighborsSet.clone()){
			OLSRNode node = nodePair.getAdvertised();
			if (!node.equals(localNode) && !isSymNeighbor(node)){
				tmpNeighOfNeigh_N2.add(node);
			}
		}		
		//B1. To ease detection of isolated nodes, we will create a backup graph to store 2hop neighbor network
		graph.clear();		
		//B2.Calculate D(y) for all nodes in tmpNeighbors_N
		//D(y) =  Node degree of a neighbor of y, excluding all members of tmpNeighbors_N and the localNode
		//that is, neighbors of my neighbours which are neither neighbors of mine nor myself
		neighborsDegree.clear();		
		for(OLSRNode node:tmpNeighbors_N){
			//Add an edge between the source and each neighbor (2, since the graph is directed)
			graph.addEdge(localNode,node,dummyWeight);
			graph.addEdge(node,localNode,dummyWeight);			
			//For every neighbor, count neighbors that follow the conditions
			OLSRSet list = (OLSRSet)neighborTable.getEntry(node).getNeighborsOfNeighbors().clone();
			int degree=0;
			for (OLSRNode tmpNeighbour:list){
				if (!tmpNeighbour.equals(localNode)&&!tmpNeighbors_N.contains(tmpNeighbour)){
					graph.addEdge(node,tmpNeighbour,dummyWeight);
					graph.addEdge(tmpNeighbour,node,dummyWeight);
					degree++;
				}
			}
			//Store degree info
			neighborsDegree.put(node,new Integer(degree));
		}
	}	
	/**
	 * Checks if the node is a symmetric neighbor of the local node
	 * @param node
	 * @return
	 */
	private boolean isSymNeighbor(OLSRNode node) {
		//The node does not have an entry in the table
		if (neighborTable.getEntry(node)==null) return false;
		
		LinkCode linkStatus = neighborTable.getEntry(node).getLinkCode();
		if (linkStatus.getLinkType()==LinkCode.SYM_NEIGH || linkStatus.getLinkType()==LinkCode.MPR_NEIGH) return true;
		else return false;
	}
}