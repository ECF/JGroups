package urv.omolsr.core;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.protocols.OMOLSR;
import org.jgroups.protocols.OMOLSRHeader;

import urv.olsr.data.OLSRNode;
import urv.omolsr.data.OMOLSRData;
import urv.util.graph.HashMapSet;

/**
 * This class process all OMOLSR received messages (DATA and CONTROL messages).
 *
 * @author Gerard Paris Aixala
 * @author Marcel Arrufat Arias
 *
 */
public class StandardHandler implements Handler{

	//	CLASS FIELDS --
	
	private OMOLSRData data;
	private OLSRNode localOLSRNode;
	private Hashtable broadcastMinNeigbour = new Hashtable();
	private OMOLSR protocolCallback;
	protected final Log log = LogFactory.getLog(this.getClass());

	//	CONSTRUCTORS --
	
	public StandardHandler(OMOLSR omolsr,OMOLSRData data,OLSRNode localOLSRNode){
		this.protocolCallback = omolsr;
		this.data = data;
		this.localOLSRNode = localOLSRNode;
	}
	
	//	OVERRIDDEN METHODS --
	
	/**
	 * Receives a multicast message and check if this message has to be forwarded
	 * We should get the headers and check if we have to forward the message
	 * We do not send the message for ourselves, since it should have been sent
	 * in the OMOLSR protocol up method
	 */
	public boolean handleIncomingDataMessage(Message msg) {
		try{
			//1. Get information of nodes which should receive the message
			Set<OLSRNode> virtualNeighbors = data.getVirtualNeighbors(localOLSRNode);
			//2. Send the message to the neighbors we have to send the message
			//	 as it is indicated in the forwarding table
			sendForwardedMessageToVirtualNeighbors(msg.copy(),virtualNeighbors);
		}catch (Exception e) {
		   e.printStackTrace();
		}
		return true;
	}
	/**
	 * Sends a new message to all direct neighbours in the multicast group
	 * (and another message to the local node) with application data and
	 * header information about the remaining nodes which the message
	 * must be delivered to
	 * @param msg An outgoing multicast message
	 */
	public void handleOutgoingDataMessage(Message msg){
		//1. Get information of nodes which should receive the message
		Set<OLSRNode> virtualNeighbors = data.getVirtualNeighbors(localOLSRNode);
		//2. Send the message to all of them
		sendOutgoingMessageToVirtualNeighbors(msg.copy(),virtualNeighbors);
		//3. Send the message to ourselves
		sendMessageToOurSelves(msg.copy());
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Register information about the minimun number of neighbours needed to
	 * perform local broadcast
	 * @param groupId
	 * @param num
	 */
	public void registerBroadcastMinNeighbour(String groupId, int num){
		broadcastMinNeigbour.put(groupId,new Integer(num));
	}
	private String getProtocolName(){
		return "OMOLSR";
	}
	/**
	 * Checks if the specified node is included in the headers, either as direct receiver or indirect receiver
	 * @param headers
	 * @param node
	 * @return
	 */
	private boolean nodeIsInHeaders(HashMapSet<OLSRNode,OLSRNode> headers, OLSRNode node){
		for (OLSRNode n1:headers.keySet()){
			if (node.equals(n1)){
				return true;
			}
			for (OLSRNode n2:headers.getSet(n1)){
				if (node.equals(n2)){
					return true;
				}
			}
		}
		return false;
	}
	
	//	PRIVATE METHODS --
	
	/**
	 * Forwards the message to the rest of nodes (virtual neighbors) that have not
	 * received the message yet
	 * @param msg
	 * @param virtualNeighbors
	 */
	private void sendForwardedMessageToVirtualNeighbors(Message msg, Set<OLSRNode> virtualNeighbors) {

		//Get information about nodes that still must receive the message
		OMOLSRHeader omolsrHeader = (OMOLSRHeader)msg.getHeader(getProtocolName());

		//These are the nodes that we should send the message to
		HashSet<OLSRNode> nodeSet = omolsrHeader.getForwardingTableEntry(localOLSRNode);

		//From this node Set, the nodes that are virtual neighbors will be the keys in the
		//new forwarding table. The others must be checked to verify if the message has to
		//be sent also to them. They will be in the forwarders list inside the forwarding table

		//If we have to send the message to some other node...

		if (nodeSet!=null && !nodeSet.isEmpty()){
			//We get the nodes that will receive directly a message from us
			Set<OLSRNode> virtualNeighborsToForwardSet = data.getVirtualNeighborsExistingInSet(localOLSRNode,nodeSet);

			//And the rest of nodes, which are the nodes that must receive a message from the ones above
			Set<OLSRNode> nonVirtualNeighborsToForwardSet = data.getNonVirtualNeighborsExistingInSet(localOLSRNode,nodeSet);

			OMOLSRHeader newOmolsrHeader = new OMOLSRHeader();
			HashMapSet<OLSRNode,OLSRNode> headers = data.getHeadersForForwardingNodes(virtualNeighborsToForwardSet,nonVirtualNeighborsToForwardSet);

			//Copy info from the original header
			newOmolsrHeader.setForwardingTable(headers);
			newOmolsrHeader.setType(OMOLSRHeader.DATA);
			newOmolsrHeader.setSrcAddress(omolsrHeader.getSrcAddress());
			newOmolsrHeader.setGroupId(omolsrHeader.groupId);

			//Once we have the headers, we can send a message to each virtualNeighbor
			//Add header info
			msg.putHeader(getProtocolName(),newOmolsrHeader);

			for (OLSRNode vnToForwardMessage:virtualNeighborsToForwardSet){
				Message msgCopy = msg.copy();
				msgCopy.setDest(vnToForwardMessage.getJGroupsAddress());
				protocolCallback.eventDown(new Event(Event.MSG,msgCopy));

			}
			//	TODO: We must maybe check that all pending nodes will receive the
			//	message. All nodes in the node set should be virtualneighbors or
			//  non virtualneighbors that are in one of the nonVntoForwardSet

			//	if there are remaining nodes, the local node will send a unicast message to
			//	these nodes

			for (OLSRNode n:nodeSet){
				if (!nodeIsInHeaders(headers,n) && !virtualNeighborsToForwardSet.contains(n)){
					Message msgCopy = msg.copy();
					OMOLSRHeader newOmolsrHeader2 = new OMOLSRHeader();
					newOmolsrHeader2.setType(OMOLSRHeader.DATA);
					newOmolsrHeader2.setSrcAddress(omolsrHeader.getSrcAddress());
					newOmolsrHeader2.setGroupId(omolsrHeader.groupId);
					msgCopy.putHeader(getProtocolName(), newOmolsrHeader2);
					msgCopy.setDest(n.getJGroupsAddress());
					System.err.println("OMOLSR: Sending msg specifically to remaining node (otherwise would not receive the message)");
					protocolCallback.eventDown(new Event(Event.MSG,msgCopy));
				}
			}
		}
	}
	/**
	 * Since it is a multicast message, the localnode must also receive this
	 * message, so we forward it to the upper layers
	 * @param msg
	 */
	private void sendMessageToOurSelves(Message msg) {
		//Get a copy of the message
		Message msgCopy = msg.copy();
		OMOLSRHeader omolsrHeader = new OMOLSRHeader();
		//Create empty headers
		HashMapSet<OLSRNode,OLSRNode> headers = new HashMapSet<OLSRNode,OLSRNode>();

		omolsrHeader.setForwardingTable(headers);
		omolsrHeader.setType(OMOLSRHeader.DATA);
		//May be not necessary, just for coherence
		omolsrHeader.setSrcAddress(localOLSRNode.getJGroupsAddress());
		//not setting groupID
		
		//Set the header on the message
		msgCopy.putHeader(getProtocolName(),omolsrHeader);
		//Set local address as destination of the message
		msgCopy.setDest(localOLSRNode.getJGroupsAddress());	
		protocolCallback.eventDown(new Event(Event.MSG,msgCopy));
	}	
	/**
	 * Sends a new original message to the virtual neighbors. The message includes
	 * a header which indicates which nodes have not received the message yet
	 * @param msg
	 * @param virtualNeighbors
	 */
	private void sendOutgoingMessageToVirtualNeighbors(Message msg, Set<OLSRNode> virtualNeighbors) {

		//Create a new Header to be sent with the message
		OMOLSRHeader omolsrHeader = new OMOLSRHeader();
		//Get headers for the source node, that is, the information that
		//indicates which are the nodes each neighbor is responsible for
		HashMapSet<OLSRNode,OLSRNode> headers = data.getHeadersForSource(localOLSRNode);
		omolsrHeader.setForwardingTable(headers);
		omolsrHeader.setType(OMOLSRHeader.DATA);
		omolsrHeader.setSrcAddress(localOLSRNode.getJGroupsAddress());
		omolsrHeader.setGroupId(msg.getDest());

		//Set the header on the message
		msg.putHeader(getProtocolName(),omolsrHeader);

		//Send a copy of the message to all the virtual neighbors
		for (OLSRNode node:virtualNeighbors){
			Message msgCopy = msg.copy();
			//System.out.println("Src: "+localOLSRNode+" setting dest as "+node.getJGroupsAddress());
			msgCopy.setDest(node.getJGroupsAddress());
			protocolCallback.eventDown(new Event(Event.MSG,msgCopy));
		}
		/* ****** ADDED NEW ******* */
		Set<OLSRNode> temporalNodes = data.getTemporalNodes();
		for(OLSRNode n:temporalNodes){
			Message msgCopy = msg.copy();
			OMOLSRHeader omolsrHeader2 = new OMOLSRHeader();
			omolsrHeader2.setType(OMOLSRHeader.DATA);
			omolsrHeader2.setSrcAddress(localOLSRNode.getJGroupsAddress());
			omolsrHeader2.setGroupId(msg.getDest());
			msgCopy.putHeader(getProtocolName(),omolsrHeader2);
			msgCopy.setDest(n.getJGroupsAddress());
			protocolCallback.eventDown(new Event(Event.MSG,msgCopy));
		}
		/* ****** ADDED NEW ******* */
	}	
}