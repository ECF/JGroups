package org.jgroups.protocols;

import java.io.Serializable;
import java.util.Properties;
import java.util.Set;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.Message;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;

import urv.olsr.data.OLSRNode;
import urv.olsr.mcast.TopologyEvent;
import urv.omolsr.data.OMOLSRNetworkGraph;


/**
 * Simple Multicast Protocol.
 * This protocol provides multicast communication by means of a simple
 * multiple unicast scheme.
 * 
 * @author Gerard Paris Aixala
 */
public class SMCAST extends Protocol {

	private OLSRNode localNode;
	private OMOLSRNetworkGraph omolsrNetworkGraph;
	private String mcast_addr_name;
	
	public SMCAST(){}		
		
	/**
     * An event is to be sent down the stack. The layer may want to examine its type and perform
     * some action on it, depending on the event's type. If the event is a message MSG, then
     * the layer may need to add a header to it (or do nothing at all) before sending it down
     * the stack using <code>down_prot.down()</code>. In case of a GET_ADDRESS event (which tries to
     * retrieve the stack's address from one of the bottom layers), the layer may need to send
     * a new response event back up the stack using <code>up_prot.up()</code>.
     */
	public Object down(Event evt) {
		Message msg;
		Object obj;
		
		switch (evt.getType()) {
			case Event.MSG:
				// get the message
				msg = (Message) evt.getArg();
				Address dest = msg.getDest();
				boolean multicast = dest == null || dest.isMulticastAddress();
				if (multicast){
					// The message is multicast

					// TODO Obtain the list of nodes that are in the multicast group
					if (omolsrNetworkGraph!=null){
						Set<OLSRNode> groupMembers =  omolsrNetworkGraph.getGroupNodes(); // CHANGED TO OMOLSRNetworkGraph
						
						// TODO Send a unicast message to each member of the group
						for (OLSRNode n: groupMembers){
							// TODO Here, we use the destination multicast port as the port of the unicast destination...
							// TODO or port=0 if dest==null
							
							Address ucastDest = new IpAddress(n.getAddress(),dest==null ? 0 : ((IpAddress)dest).getPort());
							Message ucastMsg = new Message(ucastDest,null,(Serializable)msg.getObject());
							
							// TODO Do we need a SMCAST header?? Probably not now, because upper protocols that handle multicast 
							// messages will put their own headers (NAKACK,...)
							ucastMsg.putHeader(getName(), new SMCASTHeader());
							
							Event ucastEvt = new Event(Event.MSG,ucastMsg);
							down_prot.down(ucastEvt);
						}
					}
					return null;
					
					
					//OLSRNode destNode = new OLSRNode();
					//destNode.setValue(((IpAddress) dest).getIpAddress());
					
					
					//We add the mcast addr of the group (stack) in this originating message
					//return controller.handleOutgoingDataMessage(msg,destNode,mcast_addr_name);
					
				}
				break;

		}
		
		// Pass on to the layer below us
		return down_prot.down(evt);
	}
	
	@Override
	public String getName() {
		return "SMCAST";
	}
	
	public boolean setProperties(Properties props) {
        String str;
        
        str=Util.getProperty(new String[]{Global.UDP_MCAST_ADDR, "jboss.partition.udpGroup"}, props,
                "mcast_addr", false, "224.0.0.66");
        if(str != null)
        	mcast_addr_name=str;

        return true;
	}
	
	public void start() throws Exception{
		super.start();
	}
	
	 public void stop() {
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
	public Object up(Event evt) {
		Message msg, rsp_msg;
		Object obj;
		Address coord;
		SMCASTHeader hdr;

		//log.debug("INI- Up - New event!");
		
		switch (evt.getType()) {

		case Event.SET_LOCAL_ADDRESS:

			localNode = new OLSRNode();
			localNode.setValue(((IpAddress) evt.getArg()).getIpAddress());
			//System.out.println("SMCAST: Event.SET_LOCAL_ADDRESS: "+ localNode.toString());
			
			//startController();
	
			return up_prot.up(evt);
		case Event.CONFIG:
			//System.out.println("SMCAST: Event.CONFIG"+ evt.getArg());
			
            Object ret=up_prot.up(evt);
            if(log.isDebugEnabled()) log.debug("received CONFIG event: " + evt.getArg());
            //handleConfigEvent((HashMap)evt.getArg());
            return ret;
		case Event.MSG:
			// get message
			msg = (Message) evt.getArg();
			// getHeader from protocol name
			obj = msg.getHeader(getName());
			
			if (obj == null || !(obj instanceof SMCASTHeader)) {
				return up_prot.up(evt);
			}
			hdr = (SMCASTHeader) msg.getHeader(getName());
			
			// If there is an SMCAST header, the message destination address is changed
			// TODO Is it useful?? Is the destination address used in the upper protocols?? Probably not!
			/*InetAddress mcastAddr = null;
			try {
				mcastAddr = InetAddress.getByName(mcast_addr_name);
			} catch (UnknownHostException e) {
				
				e.printStackTrace();
			}
			//IpAddress oldDest = ((IpAddress)msg.getDest());
			
			Address mcastDest = new IpAddress(mcastAddr,0);
			msg.setDest(mcastDest);*/
			
			return up_prot.up(evt);
			
		case Event.USER_DEFINED:
			Object objArg = evt.getArg();
			if (objArg instanceof TopologyEvent){
				TopologyEvent updateEvt = (TopologyEvent)objArg;

				omolsrNetworkGraph  = updateEvt.getOMOLSRNetworkGraph();
				
			}
			return up_prot.up(evt);
		default:
			log.debug("An event (not a MSG or SET_LOCAL_ADDRESS) has been received");
		
			return up_prot.up(evt); // Pass up to the layer above us
		}
	}

	
}
