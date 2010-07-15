package org.jgroups.protocols;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Header;
import org.jgroups.Message;
import org.jgroups.protocols.pbcast.NakAckHeader;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;

import urv.conf.ApplicationConfig;
import urv.olsr.data.OLSRNode;
import urv.olsr.mcast.TopologyEvent;
import urv.omolsr.core.OMOLSRController;
import urv.omolsr.core.UnicastHandlerListener;
import urv.omolsr.data.OMOLSRNetworkGraph;

/**
 * An Overlay Multicast protocol. Multicast messages are intercepted by this
 * protocol and are sent by means of several unicast messages. This protocol
 * constructs a multicast mesh in order to send multicast messages in a
 * efficient manner.
 * @author Marcel Arrufat Arias
 * @author Gerard Paris Aixala
 */
public class OMOLSR extends Protocol {

	// New vars 01-04-2008
	private OLSRNode localNode;

	//Multicast info for this stack
	private String multicastAddress;
	private int multicastPort;
	private Address mcastAddr;
	
	//Minimum number of neighbors to perform broadcast instead of unicast
	private int bcastMinNeigbours;
	private UnicastHandlerListener unicastHandlerListener;
	
	private Address localAddress;

	//TODO OMOLSR: Now, it is not a singleton
	private OMOLSRController controller;
	private boolean recomputeMstFlag;
	private InetAddress multicastInetAddress;
	
	/**
	 * An event is to be sent down the stack. The layer may want to examine its
	 * type and perform some action on it, depending on the event's type. If the
	 * event is a message MSG, then the layer may need to add a header to it (or
	 * do nothing at all) before sending it down the stack using
	 * <code>PassDown</code>. In case of a GET_ADDRESS event (which tries to
	 * retrieve the stack's address from one of the bottom layers), the layer
	 * may need to send a new response event back up the stack using
	 * <code>passUp()</code>.
	 */
	public Object down(Event evt) {
		Message msg;
		
		switch (evt.getType()) {
			case Event.MSG:
				//get message
				msg = (Message) evt.getArg();
				Address dest = msg.getDest();
				boolean multicast = dest == null || dest.isMulticastAddress();
				if (multicast){
					//In first place, check if MST must be recomputed
					if (isRecomputeMstFlag()){
						setRecomputeMstFlag(false);
						controller.computeMST();						
					}					
					// The message is addressed to a multicast group
					OMOLSRHeader header = new OMOLSRHeader();
					header.setType(OMOLSRHeader.DATA);
					header.setGroupId(mcastAddr);
					//Since OLSR changes src address we must recover this information
					//at omolsr level
					header.setSrcAddress(localAddress);
					msg.putHeader(getName(),header);					
					handleOutgoingDataMessage(msg);
					return null;
				}else {
					// Count message retransmission sent
					Header nakackHeader = msg.getHeader("NAKACK");
					if (nakackHeader!=null){
						String headerStr = ((NakAckHeader)nakackHeader).toString();
						if (headerStr.indexOf("XMIT_RSP")!=-1){							
							// Obtain received retransmited messages
							//obtainRetransmissionListStatistics(headerStr);
						}
					}			
					break;
				}
		}		
		// Pass on to the layer below us
		return down_prot.down(evt);
	}	
	public Object eventDown(Event evt){
		Message msg = (Message)evt.getArg();
		System.err.println("OMOLSR: sending message from "+localNode+" to "+msg.getDest());
		return down_prot.down(evt);
	}	
	public Object eventUp(Event evt){
		return up_prot.up(evt);
	}	
	/**
	 * @see org.jgroups.stack.Protocol#getName()
	 */
	public String getName() {
		return "OMOLSR";
	}		
	/**
	 * 
	 * @param msg
	 */
	public void sendUnicastDataMessage(Message msg){		
		send(msg);
	}
	/**
	 * sets the properties of the OMOLSR protocol.
	 * The following properties are available
	 * property: bc_port port where broadcast message will be sent

	 * @param props - a property set containing only OMOLSR properties
	 * @return returns true if all properties were parsed properly
	 *         returns false if there are unrecnogized properties in the property set
	 */
	public boolean setProperties(Properties props) {
		
		String str = props.getProperty("mcast_addr");
		if (str != null) {			 
			multicastAddress = str.toString();
			try {
				multicastInetAddress = InetAddress.getByName(multicastAddress);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			props.remove("mcast_addr");			
		}else{
			log.error("Multicast address for OMOLSR protocol is not speficied");
			return false;
		}
		str = props.getProperty("mcast_port");
		if (str != null) {			
			multicastPort = Integer.parseInt(str.toString());
			props.remove("mcast_port");			
		}else{
			log.warn("Multicast port for OMOLSR protocol is not speficied");
		}
		
		str = props.getProperty("bcast_min_neigh");
		if (str != null) {
			log.debug("Will send broadcast if at least "+bcastMinNeigbours+" neighbours present");
			bcastMinNeigbours = Integer.parseInt(str.toString());			
			props.remove("bcast_min_neigh");
		}else{
			bcastMinNeigbours = ApplicationConfig.LOCAL_BROADCAST_MIN_NEIGHBOURS;
			log.error("Minimum number of 1-hop neighbours to enable broadcast data messages is not speficied for OMOLSR protocol");	
		}		
		return true;
	}
	public void setUnicastHandlerListener(UnicastHandlerListener listener){
		this.unicastHandlerListener = listener;	
	}	
	/**
	 * Starts the protocol, sets the port where broadcast messages are received 
	 */
	public void start() throws Exception{
		super.start();
		log.debug("OMOLSR: start!! mcast="+multicastAddress+" local="+localAddress);
	}
	/**
	 * Stops the protocol, by unregistering itself from the OMcastHandler
	 */
	public void stop() {
		//controller.unregisterOmcastProtocol(multicastAddress);
	}
	/**
	 * An event was received from the layer below. Usually the current layer will want to examine
	 * the event type and - depending on its type - perform some computation
	 * (e.g. removing headers from a MSG event type, or updating the internal membership list
	 * when receiving a VIEW_CHANGE event).
	 * Finally the event is either a) discarded, or b) an event is sent down
	 * the stack using <code>PassDown</code> or c) the event (or another event) is sent up
	 * the stack using <code>PassUp</code>.
	 * <p/>
	 *
	 * @param evt - the event that has been sent from the layer below
	 */
	public Object up(Event evt) {
		Message msg;
		Object obj;		
		OMOLSRHeader hdr;
		
		switch (evt.getType()) {
		//Startup
		case Event.SET_LOCAL_ADDRESS:
			log.debug("Received local address in OMOLSR");
			
			try {
				localAddress = (Address)((IpAddress) evt.getArg()).clone();					
				localNode = new OLSRNode();
				localNode.setValue(((IpAddress)localAddress).getIpAddress());
				System.err.println("Received local node in OMOLSR:"+localNode);
			} catch (CloneNotSupportedException e) {				
				e.printStackTrace();
			}			
			// Setting local address for this group
			try {
				mcastAddr = new IpAddress(multicastAddress,multicastPort);
				//Needed when sending messages by local broadcast
				//Dst address must be multicast address
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			}			
			onStartOK();
			return up_prot.up(evt);
			
		// retrieve header and check if message contains data or control
		// information
		case Event.MSG:
			// get message
			msg = ((Message) evt.getArg());
			// getHeader from protocol name
			obj = msg.getHeader(getName());

			if (obj == null || !(obj instanceof OMOLSRHeader)) {
				return up_prot.up(evt);
			}
			hdr = (OMOLSRHeader) msg.getHeader(getName());
			//Set back src and dst Addressess before getting the message copy

			//Set back the src address, since OLSR changes src address when
			//routing messages			
			Address srcAddr = hdr.getSrcAddress();
			System.err.println("["+localNode.getAddress().getHostAddress()+"] Message src was "+msg.getSrc()+ " and now changing src to "+srcAddr);
			msg.setSrc(srcAddr);
			//Set destination multicast address
			msg.setDest(mcastAddr);
			
			// Check message type
			switch (hdr.type) {

				case OMOLSRHeader.CONTROL:
					//OMOLSR: No control messages anymore
					return null;
	
				case OMOLSRHeader.DATA:
					try{
						//In first place, check if MST must be recomputed
						if (isRecomputeMstFlag()){
							setRecomputeMstFlag(false);
							getController().computeMST();
						}						
						handleIncomingDataMessage(msg.copy()); // TODO There is no header!!
						//Not needed, it's done out of the case statement
					}
					catch (Exception e) {
						e.printStackTrace();
					}					
					break;
	
				default:
					System.err.println("Received a message without type!");
					log.debug("got OMcast header with unknown type ("
							+ hdr.type
							+ ')');
					break;
			}			
			//passUp the event to the layer above
			return up_prot.up(evt);

		case Event.VIEW_CHANGE:
			Object objArg = evt.getArg();
			if (objArg instanceof TopologyEvent){
				TopologyEvent updateEvt = (TopologyEvent)objArg;
				OMOLSRNetworkGraph omolsrNetworkGraph  = updateEvt.getOMOLSRNetworkGraph();
				localNode = (OLSRNode) updateEvt.getLocalNode().clone();
				getController().updateMulticastNetworkGraph(omolsrNetworkGraph);
				setRecomputeMstFlag(true);
				return up_prot.up(new Event(Event.VIEW_CHANGE, updateEvt));				
			}
			return up_prot.up(evt);

		default:
			log.debug("An event (not a MSG or SET_LOCAL_ADDRESS) has been received");
			return up_prot.up(evt); // Pass up to the layer above us
		}
	}
	private void createController() {
		controller = new OMOLSRController(this,localNode);
	}
	/**
	 * @return Returns the controller.
	 */
	private OMOLSRController getController() {
		//Wait for the event that creates the controller
		while (controller==null);
		return controller;
	}
	private boolean handleIncomingDataMessage(Message msg){
		return unicastHandlerListener.handleIncomingDataMessage(msg);
	}
	/**
	 * 
	 * @param msg
	 */
	private void handleOutgoingDataMessage(Message msg){
		unicastHandlerListener.handleOutgoingDataMessage(msg);
	}
	/**
	 * Returns the value of the flag
	 * @return
	 */
	private boolean isRecomputeMstFlag() {
		return recomputeMstFlag;
	}
	/**
	 * Launches bootstrapping process
	 *
	 */
	private void onStartOK() {
		// Creates the controller
		createController();
		System.err.println("OMOLSR: start!! mcast="+multicastAddress+" local="+localAddress+" localNode="+localNode);
		getController().registerOmolsrProtocol(this);
	}
	/**
	 * Sends a unicast message through the underlying channel protocol
	 * @param msg The final message to be sent
	 */
	private void send(Message msg){		
		// Generate a Message Event...
		if (msg == null)
			throw new NullPointerException("msg is null");		
		down_prot.down(new Event(Event.MSG, msg));
	}
	/**
	 * Indicates whether the MST must be recomputed before sending 
	 * a new message
	 * @param b
	 */
	private synchronized void setRecomputeMstFlag(boolean b) {
		this.recomputeMstFlag = b;
	}
}