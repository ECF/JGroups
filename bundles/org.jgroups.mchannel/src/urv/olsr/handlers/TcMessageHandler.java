package urv.olsr.handlers;

import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;
import urv.olsr.data.mpr.OLSRSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.neighbour.NeighborTableEntry;
import urv.olsr.data.topology.OLSRNodePair;
import urv.olsr.data.topology.TopologyInformationBaseEntry;
import urv.olsr.data.topology.TopologyInformationBaseTable;
import urv.olsr.mcast.MulticastAddress;
import urv.olsr.mcast.MulticastGroupsTable;
import urv.olsr.message.TcMessage;
import urv.olsr.message.ValidityTime;

/**
 * This class is in charge of performing all needed actions when a new 
 * TCMessage is received.
 * 
 * Upon receiving a TC message, the "validity time" MUST be computed
   from the Vtime field of the message header (see section 3.3.2).  The
   topology set SHOULD then be updated as follows (using section 19 for
   comparison of ANSN):

     1    If the sender interface (NB: not originator) of this message
          is not in the symmetric 1-hop neighborhood of this node, the
          message MUST be discarded.
          2    If there exist some tuple in the topology set where:

               T_last_addr == originator address AND

               T_seq       >  ANSN,

          then further processing of this TC message MUST NOT be
          performed and the message MUST be silently discarded (case:
          message received out of order).


     3    All tuples in the topology set where:

               T_last_addr == originator address AND

               T_seq       <  ANSN

          MUST be removed from the topology set.

     4    For each of the advertised neighbor main address received in
          the TC message:

          4.1  If there exist some tuple in the topology set where:

                    T_dest_addr == advertised neighbor main address, AND

                    T_last_addr == originator address,

               then the holding time of that tuple MUST be set to:

                    T_time      =  current time + validity time.

          4.2  Otherwise, a new tuple MUST be recorded in the topology
               set where:

                    T_dest_addr = advertised neighbor main address,

                    T_last_addr = originator address,

                    T_seq       = ANSN,

                    T_time      = current time + validity time.
                    
 * @author Marcel Arrufat Arias
 */
public class TcMessageHandler {

	//	CLASS FIELDS --
	
	private TopologyInformationBaseTable topologyTable;
	private NeighborTable neighborTable;
	private MulticastGroupsTable multicastGroupsTable;	
	private Object lock = new Object();	
	protected final Log log=LogFactory.getLog(getClass());
	
	//	CONSTRUCTORS --
	
	public TcMessageHandler(TopologyInformationBaseTable topologyTable, NeighborTable neighborTable,
			MulticastGroupsTable multicastGroupsTable) {
		this.topologyTable = topologyTable;
		this.neighborTable = neighborTable;
		this.multicastGroupsTable = multicastGroupsTable;
	}	
	
	//	PUBLIC METHODS --
	
	public void handleTcMessage(OLSRNode originatorNode, OLSRNode srcNode, TcMessage tcMessage, ValidityTime vTime){
		
		//We must synchronize the whole process, since different TcMessages may arrive at a time
		log.debug("** Received TC Message from "+originatorNode+ " and src :"+srcNode);
		synchronized (lock) {
			SequenceNumber seqNum = tcMessage.getAnsn();
			OLSRSet set = tcMessage.getAdvertisedNeighbors();
			
			//1 if the sender(forwarder) of the message is not a symm neighbor, discard the message
			NeighborTableEntry forwarderNTEntry = neighborTable.getEntry(srcNode);
			if (forwarderNTEntry!=null){
				LinkCode forwarderLinkStatus = forwarderNTEntry.getLinkCode();
				if (forwarderLinkStatus.getNeighborType()!=LinkCode.MPR_NEIGH && forwarderLinkStatus.getNeighborType()!=LinkCode.SYM_NEIGH){
					return;
				}
			} else {
				log.debug("["+neighborTable.getLocalNode()+"] ERROR: the src of the message ("+srcNode+") is not in the neighbour table.");
				return;
			}
			//2 & 3. Loop for all nodes and remove entries with SeqNum older than the current message
			synchronized (topologyTable.getLock()) {
				Iterator<OLSRNodePair> it = topologyTable.keySet().iterator();
				boolean newChanges = false;
				while(it.hasNext()) {
					//if the entry corresponds to the node that sent the TcMessage
					//Check the seqNum
					OLSRNodePair pair = it.next();
					if (pair.getOriginator().equals(originatorNode)){
						//if the current seqNum is greater, remove entry
						if (topologyTable.getEntry(pair).getSeqNum().compareTo(seqNum)<0){
							it.remove();
							topologyTable.remove(pair); // NEW ADDED
							newChanges =true;
						}
						//if the SeqNum in the table is greater than the received, it means that
						//we received a disordered packet --> discard packet 
						else if (topologyTable.getEntry(pair).getSeqNum().compareTo(seqNum)>0){
							continue;
						}
					}
				}
				if (newChanges) topologyTable.setTopologyTableChangedFlag(true); // NEW ADDED
			}
			int time = (int)(vTime.getVTime()*1000);
			//4. Add new entries or update old ones
			for (OLSRNode advertised : set) {
				OLSRNodePair pair = new OLSRNodePair(originatorNode, advertised);
				TopologyInformationBaseEntry entry = topologyTable.getEntry(pair);
				//If the entry already exists, update the timeStamp
				if (entry!=null){
					topologyTable.updateTimestampInEntry(pair,time);
				}else{
					//Create a new entry
					//TODO: decide how we will handle validity time
					topologyTable.addTopologyInformationBaseEntry(originatorNode,advertised,seqNum,time);
				}			
			}
			// MulticastGroups Information:
			Set<MulticastAddress> joinedGroups = tcMessage.getJoinedMulticastGroups();			
			multicastGroupsTable.updateMulticastGroups(originatorNode, joinedGroups);		
		}
	}
}