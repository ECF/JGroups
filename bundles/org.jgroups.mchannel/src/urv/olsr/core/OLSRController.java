package urv.olsr.core;

import java.net.InetAddress;
import java.util.Hashtable;

import org.jgroups.Message;
import org.jgroups.stack.IpAddress;

import urv.conf.ApplicationConfig;
import urv.conf.PropertiesLoader;
import urv.log.Log;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;
import urv.olsr.data.duplicate.DuplicateTable;
import urv.olsr.data.duplicate.DuplicateTableEntry;
import urv.olsr.data.mpr.MprSelectorSet;
import urv.olsr.data.mpr.MprSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.neighbour.NeighborsOfNeighborsSet;
import urv.olsr.data.routing.RoutingTable;
import urv.olsr.data.routing.RoutingTableEntry;
import urv.olsr.data.topology.TopologyInformationBaseTable;
import urv.olsr.handlers.HelloMessageHandler;
import urv.olsr.handlers.TcMessageHandler;
import urv.olsr.mcast.MulticastGroupsTable;
import urv.olsr.mcast.MulticastNetworkGraph;
import urv.olsr.mcast.MulticastNetworkGraphComputationController;
import urv.olsr.mcast.TopologyInformationSender;
import urv.olsr.message.HelloMessage;
import urv.olsr.message.OLSRMessage;
import urv.olsr.message.OLSRMessageSender;
import urv.olsr.message.OLSRMessageUpper;
import urv.olsr.message.OLSRPacket;
import urv.olsr.message.OLSRPacketFactory;
import urv.olsr.message.TcMessage;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * This class contains all necessary structures to
 * perform routing with OLSR (one per node (i.e. one per each different IP)
 * 
 * @author Marcel Arrufat Arias
 */
public class OLSRController implements TopologyInformationSender{

	//	CLASS FIELDS --
	
	// Static variable to store different instances of the controller (for emulation purposes)
	private static Hashtable<OLSRNode,OLSRController> table = new Hashtable<OLSRNode,OLSRController>();	
	
	private Hashtable<String,OLSRMessageUpper> olsrUpperTable = new Hashtable<String,OLSRMessageUpper>();		
	private OLSRNode localNode;
	private OLSRMessageSender messageSender;		
	//Handlers
	private HelloMessageHandler helloMessageHandler;
	private TcMessageHandler tcMessageHandler;	
	//Thread
	private OLSRThread olsrThread;	
	//Factories
	private OLSRPacketFactory olsrPacketFactory;	
	//Data
	private NeighborTable neighborTable;
	private MprSet mprSet;
	private MprSelectorSet mprSelectorSet;
	private NeighborsOfNeighborsSet neighborsOfNeighborsSet;
	private RoutingTable routingTable;
	private MulticastNetworkGraph multicastNetworkGraph;
	private MulticastGroupsTable multicastGroupsTable;	
	private TopologyInformationBaseTable topologyTable;
	private DuplicateTable duplicateTable;
	// ComputationControllers
	private MprComputationController mprComputationController;
	private RoutingTableComputationController routingTableComputationController;
	private MulticastNetworkGraphComputationController multicastNetworkGraphComputationController;
	private Log log = Log.getInstance();
	
	//	CONSTRUCTORS --
	
	private OLSRController(OLSRNode localNode, OLSRMessageSender messageSender) {
		this.localNode = localNode;
		this.messageSender = messageSender;
		initialize();
	}	
	
	//	STATIC METHODS --
	
	/**
	 * In an emulated environment, the first OLSR instance will be the messageSender
	 * @param localNode
	 * @param messageSender
	 * @return
	 */
	public static synchronized OLSRController getInstance(OLSRNode localNode, OLSRMessageSender messageSender){
		if (!table.containsKey(localNode)){
			//We launch the OLSR Thread
			table.put(localNode,new OLSRController(localNode,messageSender));
		}
		return table.get(localNode);
	}	
	
	//	OVERRIDDEN METHODS --
	
	/**
	 * This methods is used to send topology events to the above layer.
	 */
	public void sendTopologyInformationEvent() {
		for (String mcastAddr : olsrUpperTable.keySet()){
			OLSRMessageUpper upper = olsrUpperTable.get(mcastAddr);
			upper.passUpdateEvent((NetworkGraph<OLSRNode, Weight>) multicastNetworkGraph.computeContractedGraph(mcastAddr), routingTable);
		}		
		//An UpdateEvent is a periodic result of catching and computing all topology information
		//for a while. After take the result, call to the garbage collector to improve the use of memory
		//into the whole protocol stack.
		System.gc();
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Process the incoming messages from 
	 * @param msg
	 */
	public void handleIncomingControlMessage(Message msg){
		OLSRNode src = new OLSRNode(); 
		src.setValue(((IpAddress)msg.getSrc()).getIpAddress()); // the hop immediately before
		
		Object obj = msg.getObject();
		if (obj instanceof OLSRPacket){

			OLSRPacket olsrPacket = (OLSRPacket)obj;
			OLSRMessage content = olsrPacket.getContent();
			
			if (olsrPacket.getOriginator().equals(localNode) || olsrPacket.getTtl()<=0){
				return; // If ttl<=0 or the message was sent by the receiving node, the message is discarded
			}					
			// Checking the DuplicateSet:
			if (duplicateTable.containsSameAddrSeq(olsrPacket.getOriginator(),olsrPacket.getMessageSequenceNumber())){
				// The message has already been completely processed and must not be processed again
			}else {		
				if (olsrPacket.getMessageType()==OLSRPacket.HELLO_MESSAGE){
					// Process the Hello Message	
					helloMessageHandler.handleHelloMessage(olsrPacket.getOriginator(), (HelloMessage)content, olsrPacket.getVTime());
				} else if (olsrPacket.getMessageType()==OLSRPacket.TC_MESSAGE){
					//Continue passing the TC Message to the rest of the network
					defaultForwardingAlgorithm(src,olsrPacket);
					//Process TC Message
					tcMessageHandler.handleTcMessage(olsrPacket.getOriginator(),src,(TcMessage)content,olsrPacket.getVTime());	
					//Try to update the originator's node bandwidth in case it has changed
					if (PropertiesLoader.isDynamicCredit() &&
							PropertiesLoader.isThroughputOptimizationNetworkSelfKnowledgementEnabled()){
						updateBwOfOriginatorNodeInDataStructures(olsrPacket);
					}
				} else {
					log.warn("ERROR: incoming message of unknown type");
				}
			}
		}else {
			log.warn("ERROR: incoming packet of unknown type "+ obj.getClass());
		}
	}
	/**
	 * This method uses the routing table to determine 
	 * @param msg
	 * @param dest
	 * @param mcast_addr_name
	 * @return message with the destination address of the following node towards the real destination node
	 */
	public Object handleOutgoingDataMessage(Message msg, OLSRNode dest, String mcast_addr_name) {		
		OLSRNode nextHop = null;
		IpAddress newDest = null;
		//Important, get a message copy! When we change the DST address, it may affect other protocols
		Message msgCopy=msg.copy();		
		try {
			//If we aren't the final destination of the message
			if (!dest.equals(localNode)){
				RoutingTableEntry routingTableEntry = routingTable.getRoutingTableEntry(dest);
				if (routingTableEntry!=null){
					//Determine the next node in the way of the destination
					nextHop = routingTableEntry.getNextAddr();
				}else {
					//Log a failure related with the routing table
					Log.getInstance().warn("Cannot route this message!");
					Log.getInstance().increaseLostDataMessage();
					return null;
				}
			} else {
				//Continue redirecting the message to the next hop
				nextHop = dest;				
			}
				
			// TODO Here, we use the destination port as the port of the next hop...
			newDest = new IpAddress(nextHop.getAddress(),((IpAddress)msgCopy.getDest()).getPort()); 
			msgCopy.setDest(newDest);
			
			InetAddress srcAddr = null;
			if (msgCopy.getSrc()==null){
				srcAddr = localNode.getAddress();
			} else {
				srcAddr = ((IpAddress)msgCopy.getSrc()).getIpAddress();
			}
			//Log information about the redirection of a message
			Log.getInstance().info(srcAddr.getHostAddress()+"-->...-->"+
					localNode.getAddress().getHostAddress()+"--> "+
					nextHop.getAddress().getHostAddress()+"-->...-->"+
					dest.getAddress().getHostAddress());
		} catch (Exception e){
			e.printStackTrace();
		}		
		return messageSender.sendDataMessage(msgCopy,dest,mcast_addr_name);
	}	
	/**
	 * Registers an OLSR protocol in order to forward an incoming data message to 
	 * the correct protocol stack (depending on the mcast_addr_name)
	 * @param mcast_addr_name
	 * @param olsrProtocol
	 */
	public void registerMessageUpper(String mcast_addr_name,OLSRMessageUpper olsrProtocol) {
		olsrUpperTable.put(mcast_addr_name,olsrProtocol);
	}
	public void registerMulticastGroup(String mcast_addr_name) {
		multicastGroupsTable.registerMulticastGroup(localNode,mcast_addr_name);		
	}
	public void sendExtraTCMessage() {
		olsrThread.setExtraTCMessage(true);
	}
	/**
 	 * Forwards an incoming data message to the correct protocol stack 
 	 * (depending on the mcast_addr_name) 
	 * @param msg
	 * @param mcastAddr
	 */
	public void sendMessageToStack(Message msg, String mcastAddr) {		
		if (olsrUpperTable.get(mcastAddr)!=null){
			olsrUpperTable.get(mcastAddr).sendMessageUp(msg);
		}else{			
			System.err.println("Error: Could not send the message to the selected protocol stack");
			System.err.println("There is no OLSR protocol registered in the OLSRUpperTable");
		}		
	}
	public void unregisterMulticastGroup(String mcast_addr_name) {
		multicastGroupsTable.unregisterMulticastGroup(localNode,mcast_addr_name);
	}
		
	//	PRIVATE METHODS --
	
	/**
	 * The default forwarding algorithm is the following:

     1    If the sender interface address of the message is not detected
          to be in the symmetric 1-hop neighborhood of the node, the
          forwarding algorithm MUST silently stop here (and the message
          MUST NOT be forwarded).

     2    If there exists a tuple in the duplicate set where:

               D_addr    == Originator Address

               D_seq_num == Message Sequence Number

          Then the message will be further considered for forwarding if
          and only if:

               D_retransmitted is false, AND
               
               the (address of the) interface which received the message
               is not included among the addresses in D_iface_list

     3    Otherwise, if such an entry doesn't exist, the message is
          further considered for forwarding.

   If after those steps, the message is not considered for forwarding,
   then the processing of this section stops (i.e., steps 4 to 8 are
   ignored), otherwise, if it is still considered for forwarding then
   the following algorithm is used:

     4    If the sender interface address is an interface address of a
          MPR selector of this node and if the time to live of the
          message is greater than '1', the message MUST be retransmitted
          (as described later in steps 6 to 8).

     5    If an entry in the duplicate set exists, with same Originator
          Address, and same Message Sequence Number, the entry is
          updated as follows:

               D_time    = current time + DUP_HOLD_TIME.

               The receiving interface (address) is added to
               D_iface_list.

               D_retransmitted is set to true if and only if the message
               will be retransmitted according to step 4.

          Otherwise an entry in the duplicate set is recorded with:

               D_addr    = Originator Address

               D_seq_num = Message Sequence Number

               D_time    = current time + DUP_HOLD_TIME.

               D_iface_list contains the receiving interface address.

               D_retransmitted is set to true if and only if the message
               will be retransmitted according to step 4.

   		If, and only if, according to step 4, the message must be
   		retransmitted then:

   	 	6    The TTL of the message is reduced by one.

     	7    The hop-count of the message is increased by one
     	
     	8    The message is broadcast on all interfaces (Notice: The
          	 remaining fields of the message header SHOULD be left
          	 unmodified.)
     
	 * @param src
	 * @param olsrPacket
	 * @return true if the message is retransmitted
	 */
	private boolean defaultForwardingAlgorithm(OLSRNode src,OLSRPacket olsrPacket){		
		if (neighborTable.isSymmetricNeighbor(src)){
			// If the sender of the message is in the symmetric 
			// 1-hop neighborhood, the message can be forwarded			
			OLSRNode originator = olsrPacket.getOriginator();
			SequenceNumber seqNumber = olsrPacket.getMessageSequenceNumber();			
			if (duplicateTable.containsSameAddrSeq(originator,seqNumber)){
				if (duplicateTable.isRetransmitted(originator,seqNumber)){
					return false; // The message will not be retransmitted again
				}
			}
			boolean willRetransmit = false;
			if (mprSelectorSet.contains(src)){ // If the sender is an MPR selector, the message is forwarded
				if (olsrPacket.getTtl()>1){
					willRetransmit = true;
				}
			}			
			// Updating DuplicateSet
			DuplicateTableEntry newEntry = new DuplicateTableEntry(originator,seqNumber);
			
			// TODO update expiring time
			duplicateTable.addEntryWithTimestamp(newEntry, new Boolean(willRetransmit), DuplicateTable.DUP_HOLD_TIME);
			// Retransmission
			if (willRetransmit){
				olsrPacket.decreaseTtl();
				olsrPacket.increaseHopCount();				
				Message msg = new Message(ApplicationConfig.BROADCAST_ADDRESS,null,olsrPacket);
				messageSender.sendControlMessage(msg);
				return true;
			}
		}
		return false;
	}	
	/**
	 * Creates all necessary data structures and it also 
	 * should launch threads for data dissemination
	 *
	 */
	private void initialize(){		
		// Initialize data structures
		this.neighborsOfNeighborsSet = new NeighborsOfNeighborsSet();
		this.neighborTable = new NeighborTable(localNode,neighborsOfNeighborsSet);
		this.mprSet = new MprSet(localNode);
		this.mprSelectorSet = new MprSelectorSet(localNode);
		this.duplicateTable = new DuplicateTable(localNode);
		this.routingTable = new RoutingTable(localNode);		
		this.topologyTable = new TopologyInformationBaseTable(localNode);		
		this.multicastGroupsTable = new MulticastGroupsTable();
		this.multicastNetworkGraph = new MulticastNetworkGraph(multicastGroupsTable,localNode);
				
		// Controllers and information processors 
		this.helloMessageHandler = new HelloMessageHandler(neighborTable,localNode);
		this.helloMessageHandler.registerMprSelectorSet(mprSelectorSet);		
		this.mprComputationController = new MprComputationController(neighborTable,neighborsOfNeighborsSet,mprSet,localNode);
		this.routingTableComputationController = new RoutingTableComputationController(neighborTable, routingTable, 
				topologyTable, neighborsOfNeighborsSet, localNode);		
		this.tcMessageHandler = new TcMessageHandler(topologyTable,neighborTable,multicastGroupsTable);		
		this.multicastNetworkGraphComputationController = new MulticastNetworkGraphComputationController(multicastNetworkGraph,
				neighborTable,topologyTable,localNode);		
		
		// Factories		
		this.olsrPacketFactory = new OLSRPacketFactory(localNode);
		
		// Registering loggable classes
		Log log = Log.getInstance();
		log.registerLoggable(this.neighborTable.getClass().getName(), this.neighborTable);
		log.registerLoggable(this.routingTable.getClass().getName(), this.routingTable);
		log.registerLoggable(this.topologyTable.getClass().getName(), this.topologyTable);
		log.registerLoggable(this.mprSet.getClass().getName(), this.mprSet);
		log.registerLoggable(this.multicastNetworkGraph.getClass().getName(), this.multicastNetworkGraph);
		log.registerLoggable(this.multicastGroupsTable.getClass().getName(),this.multicastGroupsTable);
		
		// Periodic thread
		this.olsrThread = new OLSRThread(messageSender,neighborTable,mprComputationController,
				routingTableComputationController,mprSelectorSet,olsrPacketFactory,
				topologyTable,duplicateTable,(TopologyInformationSender)this,
				multicastNetworkGraphComputationController,multicastGroupsTable,localNode);
		this.olsrThread.start();		
	}
	/**
	 * This method updates the bandwidth of the main data structures when the originator
	 * node of the TcMessage has a different bw
	 * 
	 * @param packet
	 */
	private synchronized void updateBwOfOriginatorNodeInDataStructures(OLSRPacket packet) {
		//If this node is not already in out table, exit
		if (routingTable.getRoutingTableEntry(packet.getOriginator())==null) return;
		//Get the current bw data of this node in our table
		OLSRNode unUpdatedNeighbor = routingTable.getRoutingTableEntry(packet.getOriginator()).getDestAddr();
		boolean isBwOfOriginatorNodeChanged = unUpdatedNeighbor.getBandwithCoefficient()>
			packet.getOriginator().getBandwithCoefficient();
		//If its value has changed, update the bandwidth everywhere
		if (isBwOfOriginatorNodeChanged){
			neighborTable.updateBwOf(packet.getOriginator());			
			mprSet.updateBwOf(packet.getOriginator());			
			mprSelectorSet.updateBwOf(packet.getOriginator());			
			neighborsOfNeighborsSet.updateBwOf(packet.getOriginator());			
			multicastGroupsTable.updateBwOf(packet.getOriginator());			
			multicastNetworkGraph.updateBwOf(packet.getOriginator());			
			routingTable.updateBwOf(packet.getOriginator());
		}		
	}
}