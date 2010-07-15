package urv.olsr.handlers;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.MprSelectorSet;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.neighbour.NeighborTableEntry;
import urv.olsr.message.HelloMessage;
import urv.olsr.message.ValidityTime;
import urv.util.graph.HashMapSet;

/**
 * This class is in charge of performing all needed actions when a new 
 * HelloMessage is received
 * Tables (Mpr,MprSelector,Neighbour of Neighbours) are updated when 
 * a new message is received
 * 
 * MPR computation is asked to be performed again when a neighbor becomes
 * symmetric neighbor or we add a new two-hop neighbor
 * 
 * OnTableChange is invoked when a new entry is added to the neighbor table
 * or when there are new NoNs
 * 
 * The "Originator Address" of a HELLO message is the main address of
   the node, which has emitted the message.

   Upon receiving a HELLO message from a symmetric neighbor, a node
   SHOULD update its 2-hop Neighbor Set.  Notice, that a HELLO message
   MUST neither be forwarded nor be recorded in the duplicate set.

   Upon receiving a HELLO message, the "validity time" MUST be computed
   from the Vtime field of the message header (see section 3.3.2).

   If the Originator Address is the main address of a
   L_neighbor_iface_addr from a link tuple included in the Link Set with

          L_SYM_time >= current time (not expired)

   (in other words: if the Originator Address is a symmetric neighbor)
   then the 2-hop Neighbor Set SHOULD be updated as follows:
   1    for each address (henceforth: 2-hop neighbor address), listed
          in the HELLO message with Neighbor Type equal to SYM_NEIGH or
          MPR_NEIGH:

          1.1  if the main address of the 2-hop neighbor address = main
               address of the receiving node:

                    silently discard the 2-hop neighbor address.

               (in other words: a node is not its own 2-hop neighbor).

          1.2  Otherwise, a 2-hop tuple is created with:

                    N_neighbor_main_addr =  Originator Address;

                    N_2hop_addr          =  main address of the
                                            2-hop neighbor;

                    N_time               =  current time
                                            + validity time.


               This tuple may replace an older similar tuple with same
               N_neighbor_main_addr and N_2hop_addr values.

     2    For each 2-hop node listed in the HELLO message with Neighbor
          Type equal to NOT_NEIGH, all 2-hop tuples where:

               N_neighbor_main_addr == Originator Address AND

               N_2hop_addr          == main address of the
                                       2-hop neighbor are deleted.
 * 
 * @author Marcel Arrufat Arias
 */
public class HelloMessageHandler {

	//	CLASS FIELDS --
	
	private NeighborTable neighborTable;
	private OLSRNode localOLSRNode;
	private MprSelectorSet mprSelectorSet;
	private boolean newChanges;	
	//temporal lists of nodes
	private OLSRSet tmpMprSet,tmpMprSelectorSet;	
	private Object lock = new Object();
	protected final Log log=LogFactory.getLog(getClass());
	
	//	CONSTRUCTORS --
	
	/**
	 * The Handler will update Neighbor table depending on the HELLO messages 
	 * received
	 * @param neighborTable
	 * @param localNode
	 */
	public HelloMessageHandler(NeighborTable neighborTable,OLSRNode localNode) {
		this.neighborTable = neighborTable;
		this.localOLSRNode = localNode;
		//Initialize tmp lists
		tmpMprSelectorSet = new OLSRSet();
		tmpMprSet = new OLSRSet();
	}	
	
	//	PUBLIC METHODS --
	
	/**
	 * Updates neighborTable with the new information coming from this message
	 * <li>
	 * @param source the node that send the Hello Message
	 * @param helloMessage the Hello Message
	 * @param vTime message validity time (in seconds)
	 */
	public void handleHelloMessage(OLSRNode source,HelloMessage helloMessage, ValidityTime vTime){
		log.debug("** Received HELLO Message at "+localOLSRNode +" from "+source);
		//We must synchronize the whole process, since different HelloMessages may arrive at a time
		synchronized (lock) {
			newChanges=false;			
			//Empty temporal sets for MPR and MPRSelectors and 2-hop neighbors
			preHandleMessage();
			
			//Get hello packet info
			HashMapSet<LinkCode,OLSRNode> messageInfo = helloMessage.getMessageInfo();
			NeighborTableEntry entry;
			LinkCode sourceLinkStatus = new LinkCode(LinkCode.NOT_NEIGH,LinkCode.ASYM_LINK);
			
			//If the entry does not exist, create a new one (empty)
			if (!neighborTable.entryExists(source)){				
				entry = new NeighborTableEntry(source,sourceLinkStatus,new OLSRSet());	
			}
			//if the entry exists, recover the entry
			else{				
				entry = neighborTable.getEntry(source);
			}	
			//Now, obtain information from the 2-hop nodes, and after that store back the information
			//in the neighbor table			
			//Status from the receiving node (one common linkStatus and a list of neighbours
			//with that status

			OLSRSet nonSet = entry.getNeighborsOfNeighbors();
			OLSRSet listAdd = new OLSRSet();
			OLSRSet listDel = new OLSRSet();			
			
			//Populating the 2-hop neighbor set
			for (LinkCode linkStatus:messageInfo.keySet()){
				Set<OLSRNode> neighborFromSource = messageInfo.getSet(linkStatus);
				
				for (OLSRNode node:neighborFromSource){
					//if the address of the 2-hop neighbour == address of receiver --> discard
					if (node.equals(localOLSRNode)){
						checkSourceIsMPRSelector(entry,linkStatus,source);
						continue;
					}					
					//Add the entry as 2 hop neighbour
					if (linkStatus.getNeighborType()!=LinkCode.NOT_NEIGH){
						listAdd.add(node);						
					}
					//Remove 2 hop neighbours that are set to NOT_NEIGH
					else if (linkStatus.getNeighborType()==LinkCode.NOT_NEIGH){
						listDel.add(node);
					}					
				}			
			}			
			//1.Delete nodes from nonSet that are included in listDel
			for(OLSRNode n:listDel){
				if (nonSet.contains(n)){
					nonSet.remove(n);
					newChanges=true;
					neighborTable.setRecomputeMprFlag(true);
				}
			}			
			//2.Delete nodes from nonSet that are not included in listAdd
			Iterator it = nonSet.iterator();
			while (it.hasNext()){
				OLSRNode n = (OLSRNode) it.next();
				if (!listAdd.contains(n)){
					it.remove();
					newChanges=true;
					neighborTable.setRecomputeMprFlag(true);
				}
			}			
			//3.Add nodes to nonSet that are included in listAdd
			for(OLSRNode n:listAdd){
				if (!nonSet.contains(n)){
					nonSet.add(n);
					newChanges=true;
					neighborTable.setRecomputeMprFlag(true);
				}
			}
			int msecTime = (int)(vTime.getVTime()*1000);	
			//Add to neighbor table if needed
			if (!neighborTable.entryExists(source)){
				neighborTable.addNeighborEntryWithTimestamp(source,entry,msecTime);
				//if we have a new entry, recompute NoNs and notify of changes
				newChanges=true;
			} else{
				//if the entry exists, simply update timestamp
				neighborTable.updateTimestampInEntry(source,msecTime);				
			} if (newChanges) neighborTable.onTableChange();
			postHandleMessage();
		}
	}	
	public void registerMprSelectorSet(MprSelectorSet mprSelectorSet){
		this.mprSelectorSet = mprSelectorSet;
	}	
	
	//	PRIVATE METHODS --
	
	/**
	 * Adds a new node to the tmp selector set
	 * @param source
	 */
	private void addToMPRSelectorSet(OLSRNode source) {
		tmpMprSelectorSet.add(source);
	}
	/**
	 * Checks whether the node that sent the Hello Message chose the local node
	 * as MPR
	 * @param entry
	 * @param linkStatus
	 * @param source
	 */
	private void checkSourceIsMPRSelector(NeighborTableEntry entry, LinkCode linkStatus, OLSRNode source) {
		LinkCode entryLinkCode = entry.getLinkCode();
		//If the source node that send the Hello message has selected us as MPR, 		
		if (linkStatus.getNeighborType()==LinkCode.MPR_NEIGH){
			//Add the node to the MPR selector Set
			addToMPRSelectorSet(entry.getNeighbor());
		} else {
			removeFromMPRSelectorSet(entry.getNeighbor());
		}		
		//As the local node was an entry in the Hello Message, it implies that a 
		//symmetric node exists between the local node and source
		if (entryLinkCode.getNeighborType()==LinkCode.NOT_NEIGH){
			entryLinkCode.setNeighborType(LinkCode.SYM_NEIGH);
			//MPR set must be recomputed
			neighborTable.setRecomputeMprFlag(true);			
			//Routing table must be recomputed
			neighborTable.setNeighborTableChangedFlag(true);
		}
	}
	/**
	 * Copies back the collected information to the lists of MPR and NoN
	 *
	 */
	private void postHandleMessage() {		
		//1. We already will have recomputed the MPR Set, since we marked
		//the flag to recompute the MPRs 
		//2. We already have information about MPRSelectors, since
		//it is added as the Hello Message is processed
		
		//We must check if there are old neighbors in the mpr selector set
		removeOldMPRSelectors();
		if (!mprSelectorSet.equals(tmpMprSelectorSet)){
			mprSelectorSet.setCopyOfSet(tmpMprSelectorSet);
		}
	}
	/**
	 * Empties the created lists in order to store new information
	 * when a new HelloMessage arrives
	 *
	 */
	private void preHandleMessage() {
		tmpMprSelectorSet.setCopyOfSet(mprSelectorSet);
		tmpMprSet.clear();
	}
	/**
	 * Removes a node from the tmp selector set
	 * @param source
	 */
	private void removeFromMPRSelectorSet(OLSRNode source){
		tmpMprSelectorSet.remove(source);
	}	
	/**
	 * This method checks that only current neighbors are in the 
	 * mpr selector set
	 *
	 */
	private void removeOldMPRSelectors() {
		Iterator<OLSRNode> it = tmpMprSelectorSet.iterator();
		while (it.hasNext()){
			OLSRNode neighTableNode = it.next();
			if (neighborTable.getEntry(neighTableNode)==null){
				//remove from the mpr selector set, since it is not a neighbor
				it.remove();
			}
		}		
	}
}