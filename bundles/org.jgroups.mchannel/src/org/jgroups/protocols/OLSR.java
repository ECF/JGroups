package org.jgroups.protocols;

import java.util.Properties;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.Message;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;

import urv.bwcalc.BwData;
import urv.conf.PropertiesLoader;
import urv.log.Log;
import urv.olsr.core.OLSRController;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.routing.RoutingTable;
import urv.olsr.mcast.TopologyEvent;
import urv.olsr.message.OLSRMessageSender;
import urv.olsr.message.OLSRMessageUpper;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 *
 * @author Gerard Paris Aixala
 */
public class OLSR extends Protocol implements OLSRMessageSender, OLSRMessageUpper{

	//	CLASS FIELDS --
	
	private OLSRNode localNode;
	private OLSRController controller;
	private String mcast_addr_name;
	
	//	CONSTRUCTORS --
	
	public OLSR(){
		// Required by org.jgroups.stack.Protocol
	}

	//	OVERRIDDEN METHODS --
	
	/**
     * An event is to be sent down the stack. The layer may want to examine its type and perform
     * some action on it, depending on the event's type. If the event is a message MSG, then
     * the layer may need to add a header to it (or do nothing at all) before sending it down
     * the stack using <code>down_prot.down()</code>. In case of a GET_ADDRESS event (which tries to
     * retrieve the stack's address from one of the bottom layers), the layer may need to send
     * a new response event back up the stack using <code>up_prot.up()</code>.
     */
	@Override
	public Object down(Event evt) {
		Message msg;
		
		switch (evt.getType()) {
			case Event.MSG:
				// get the message
				msg = ((Message) evt.getArg()).copy();
				Address dest = msg.getDest();
				boolean multicast = dest == null || dest.isMulticastAddress();
				if (!multicast){
					// The message is unicast
					OLSRNode destNode = new OLSRNode();
					destNode.setValue(((IpAddress) dest).getIpAddress());
					// Setting the source address of the message: this address will not change
					// along the message path.
					msg.setSrc(new IpAddress(localNode.getAddress(),PropertiesLoader.getUnicastPort()));
					//We send the message through the first stack (only the first stack routes messages
					//through the network)
					
					//We add the mcast addr of the group (stack) in this originating message
					return controller.handleOutgoingDataMessage(msg,destNode,mcast_addr_name);
				}
				break;
		}
		// Pass on to the layer below us
		return down_prot.down(evt);
	}
	@Override
	public String getName() {
		return "OLSR";
	}
	/**
	 * Passes an update (VIEW_CHANGE) event up to the stack
	 */
	@Override
	public Object passUpdateEvent(NetworkGraph<OLSRNode, Weight> networkGraph, RoutingTable routingTable) {
		return up_prot.up(new Event(Event.VIEW_CHANGE,new TopologyEvent(
				networkGraph, routingTable, localNode)));
	}
	@Override
	public Object sendControlMessage(Message msg){
		OLSRHeader header = new OLSRHeader();
		header.setType(OLSRHeader.CONTROL);
		msg.putHeader(getName(),header);
		log.debug("Sending control message...");
		return send(msg);
	}
	@Override
	public Object sendDataMessage(Message msg, OLSRNode finalDest,String mcast_addr_name){
		OLSRHeader header = new OLSRHeader();
		header.setType(OLSRHeader.DATA);
		header.setDest(finalDest);
		//Add information of group in order to be able to demultiplex messages
		//when a message arrives to the final node and send it to the
		//corresponding stack
		header.setMcastAddress(mcast_addr_name);
		msg.putHeader(getName(),header);		
		return send(msg);
	}
	/**
	 * Creates a new event with the message and sends a the message up
	 * in this protocol stack
	 */
	@Override
	public Object sendMessageUp(Message msg) {
		return up_prot.up(new Event(Event.MSG,msg.copy()));
	}
	@Override
	public boolean setProperties(Properties props) {
        String str;
        str=Util.getProperty(new String[]{Global.UDP_MCAST_ADDR, "jboss.partition.udpGroup"}, props,
                "mcast_addr", false, "224.0.0.66");
        if(str != null)
        	mcast_addr_name=str;

        return true;
	}
	@Override
	public void start() throws Exception{
		super.start();
		// When this method is invoked, the local address is not set yet!!
	}
	@Override
	public void stop() {
		controller.unregisterMulticastGroup(mcast_addr_name);
	}
	 /**
     * An event was received from the layer below. Usually the current layer will want to examine
     * the event type and - depending on its type - perform some computation
     * (e.g. removing headers from a MSG event type, or updating the internal membership list
     * when receiving a VIEW_CHANGE event).
     * Finally the event is either a) discarded, or b) an event is sent down
     * the stack using <code>down_prot.down()</code> or c) the event (or another event) is sent up
     * the stack using <code>up_prot.up()</code>.
     */
	@Override
	public Object up(Event evt) {
		Message msg;
		Object obj;
		OLSRHeader hdr;

		switch (evt.getType()) {

		case Event.SET_LOCAL_ADDRESS:
			localNode = new OLSRNode();
			localNode.setValue(((IpAddress) evt.getArg()).getIpAddress());
			startController();
			return up_prot.up(evt);
			
		case Event.CONFIG:
            Object ret=up_prot.up(evt);
            if(log.isDebugEnabled()) log.debug("received CONFIG event: " + evt.getArg());
            return ret;
            
		case Event.MSG:
			// get message
			msg = (Message) evt.getArg();			
			// getHeader from protocol name
			obj = msg.getHeader(getName());
			if (obj == null || !(obj instanceof OLSRHeader)) {
				//OLSR will also use the BW_CALC messages to perform the throughput
				//and the routes in the network
				obj = msg.getHeader(BW_CALC.name);
				if (obj!=null && obj instanceof BwCalcHeader) {
					updateBandwidth(msg);
					return up_prot.up(evt);
				}else return up_prot.up(evt);
			}
			hdr = (OLSRHeader) msg.getHeader(getName());
			
			// Check message type
			switch (hdr.type) {

				case OLSRHeader.CONTROL:
					controller.handleIncomingControlMessage(msg);
					break;

				case OLSRHeader.DATA:										
					OLSRNode finalDest = hdr.getDest();
					if (finalDest.equals(localNode)){
						// The message should be delivered to the local node
						Log.getInstance().incresaseSuccessfulDeliveredMessages();
						//if the message is for the local node, forward the packet to the correct stack

						//Check group name/multicast address
						//Get the mcastAddr from the message
						String mcastAddr=hdr.getMcastAddress();
						controller.sendMessageToStack(msg,mcastAddr);
					}else {
						// The message should be forwarded
						return controller.handleOutgoingDataMessage(msg,finalDest,hdr.getMcastAddress());
					}
					break;
					
				default:
					log.debug("got OLSR header with unknown type (" + hdr.type + ')');
					break;
			}
			return null;
		default:
			log.debug("An event (not a MSG or SET_LOCAL_ADDRESS) has been received" );
			return up_prot.up(evt); // Pass up to the layer above us
		}
	}

	//	PRIVATE METHODS --
	
	/**
	 * Sends a unicast message through the underlying channel protocol
	 * @param msg The final message to be sent
	 */
	private Object send(Message msg){
		// Generate a Message Event...
		if (msg == null) throw new NullPointerException("msg is null");
		return down_prot.down(new Event(Event.MSG, msg));
	}
	private void startController() {
		Log.getInstance().info("OLSR will start with localNode="+localNode+"!\n");
		controller = OLSRController.getInstance(localNode,this);
		controller.registerMessageUpper(mcast_addr_name,this);
		controller.registerMulticastGroup(mcast_addr_name);
		// Send a TC message with the updated group membership information (this channel is a new group)
		controller.sendExtraTCMessage();
	}
	/**
	 * This method will update the information about our bandwidth capacity.
	 *  
	 * @param msg
	 */
	private void updateBandwidth(Message msg) {
		BwData bd = (BwData) msg.getObject();
		//The coefficient or weight of each node will be defined as following:		
		//Bandwidth_coefficient = base_weight + 1/maxIncomingBytes + 1/maxIncomingPackets		
		//The best node is a combination of the best connection (bytes) and best processor (packets)
		localNode.setBwBytesCapacity(bd.getMaxIncomingBytes());
		localNode.setBwMessagesCapacity(bd.getMaxIncomingPackets());
		float bandwidthCoefficient = 10000000.0f / new Float(bd.getMaxIncomingBytes()).floatValue()
			+ 1000.0f / new Float(bd.getMaxIncomingPackets()).floatValue();
		localNode.setBandwithCoefficient(bandwidthCoefficient);	
		System.out.println("NEW BW_COEF PER NODE: "+localNode);					
	}
}