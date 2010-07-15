package urv.machannel;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.channels.InterruptibleChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.ChannelClosedException;
import org.jgroups.ChannelNotConnectedException;
import org.jgroups.MembershipListener;
import org.jgroups.Message;
import org.jgroups.MessageListener;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;

import urv.conf.PropertiesLoader;
import urv.olsr.data.OLSRNode;
import urv.olsr.mcast.MulticastAddress;
import urv.olsr.mcast.TopologyEvent;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * This class provides an implementation of the MChannel interface
 * in order to group communication with topology awareness.
 * 
 * @author Marcel Arrufat
 * @author Gerard París
 * @author Raúl Gracia
 * 
 * @version $Revision

 */
public class MChannelImpl  implements MChannel {
		
	//	CLASS FIELDS --

	//Multicast address for this MChannel instance
	private MulticastAddress mcastAddr;
	//Channel Name
	private String channelId;
	//Graph that represents the underlying topology
	private NetworkGraph<OLSRNode,Weight> graph = new NetworkGraph<OLSRNode, Weight>();
    private View view;
	public void setView(View view) {
		this.view = view;
	}

	private int seqNumber=0;
	
	// added by me
	private Channel channel;
    public Channel getChannel() {
		return channel;
	}

	protected Thread          receiver_thread=null;
    public Thread getReceiver_thread() {
		return receiver_thread;
	}

	protected final Log       log=LogFactory.getLog(getClass());
    protected final List      membership_listeners=new ArrayList();
	protected final HashMap   listeners=new HashMap(); // keys=identifier (Serializable), values=MessageListeners
	protected MessageListener listener=null;           // main message receiver

    //	CONSTRUCTORS --
		
	public MChannelImpl(Channel channel, MulticastAddress mcastAddr, String channelName ){
		//In the super constructor is started the channel, because is called the start() method
//		super(channel);
		this.channel = channel;
		this.channelId = channelName;
		this.mcastAddr = mcastAddr;
	}

	//	OVERRIDDEN METHODS --	
	
	public MChannelImpl() {
	}

	@Override
	public Address getLocalAddress(){
		return channel.getLocalAddress();
	}
	@Override
	public NetworkGraph<OLSRNode,Weight> getNetworkGraph(){
		return graph;
	}
	@Override
	public synchronized View getView() {
		return (view == null) ? null : (View) view.clone();
	}
	@Override
	public String getChannelName() {
		return channelId;
	}
	
    /**
     * Reentrant run(): message reception is serialized, then the listener is notified of the
     * message reception
     */
    public void run() {
        Object obj;
        while(receiver_thread != null && Thread.currentThread().equals(receiver_thread)) {
            try {
                obj=receive(0);
                if(obj == null)
                    continue;
                //If we receive information about the current topology
                //store this info
                if(obj instanceof Message) {
                	//Change, we intercept seq Numbers
                    handleMessage(getReceivedMessage((Message)obj));
	            } else if(obj instanceof View) {
                    notifyViewChange((View)obj);
                }
            }catch(ChannelNotConnectedException conn) {
                Address local_addr=getLocalAddress();
                if(log.isTraceEnabled()) log.trace('[' + (local_addr == null ? "<null>" : local_addr.toString()) +
                        "] channel not connected, exception is " + conn);
                Util.sleep(1000);
                receiver_thread=null;
                break;
            }catch(ChannelClosedException closed_ex) {
                Address local_addr=getLocalAddress();
                if(log.isTraceEnabled()) log.trace('[' + (local_addr == null ? "<null>" : local_addr.toString()) +
                        "] channel closed, exception is " + closed_ex);
                receiver_thread=null;
                break;
            }
            catch(Throwable e) {}
        }
    }	
	
    /* **********************************************
     * 				 MESSAGE DELIVERY
     * ********************************************/
	
//	/**
//	 * Sends a message to all peers in a group
//	 */
//	@Override
//	public void send(Message msg) {
//		try {
//			send(msg);
//		} catch (Exception e) {
//			System.err.println("Could not send message "+msg);
//			e.printStackTrace();
//		}
//	}
//    /**
//     * Sends a message to a selected peer
//     */
//	@Override
//	public void send(Address dst, Address src, Serializable content) {
//		Message msg=createMessage(dst,content);
//		try {
//			send(channelId,msg);
//		} catch (Exception e) {
//			System.err.println("Could not send message "+msg);
//			e.printStackTrace();
//		}
//	}	
	/**
	 * Sends a message to all the neighbors of the localNode in this group
	 */
	@Override
	public void sendToNeighbors(Serializable content) {
		//If we have neighbors, send the message to them
		if (graph!=null){
			OLSRNode localNode = new OLSRNode();
			localNode.setValue(getInetAddress(getLocalAddress()));
			for (OLSRNode node : graph.getNeighbours(localNode)){
				try {
					send(createMessage(node.getJGroupsAddress(),content));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}		
		}
	}
	@Override
	public synchronized List<InetAddress> getInetAddressesOfGroupMembers () {
		List<InetAddress> addresses = new ArrayList<InetAddress>();
		if (getView()==null) return addresses;
		for (Address jGroupsAddress : getView().getMembers()){
			addresses.add(getInetAddress(jGroupsAddress));
		}
		return addresses;
	}
	@Override
	public void close() {
        stop();		
	}
	
    protected void notifyViewChange(View v) {
		System.out.println("New membership received!!! ["+ view.size() + "]" );		
		if (v instanceof TopologyEvent){
			graph = ((TopologyEvent)v).getOMOLSRNetworkGraph().getNetworkGraphCopy();
		}
        MembershipListener l;

        if(v == null) return;
        for(Iterator it=membership_listeners.iterator(); it.hasNext();) {
            l=(MembershipListener)it.next();
            try {
                l.viewAccepted(v);
            }
            catch(Throwable ex) {
                if(log.isErrorEnabled()) log.error("exception notifying " + l + " of view(" + v + ")", ex);
            }
        }
    }
	
	//	PRIVATE METHODS --
	
	/**
	 * Creates a message. It creates a wrapped message for emulation, or a regular
	 * message for real applications
	 */
	private Message createMessage(Address dst, Serializable content) {
		Message msg;
		InetAddress dstInetAddress = getInetAddress(dst);
		//Set destination if it is null (it is a Multicast address)
		if (dstInetAddress==null) dstInetAddress = mcastAddr.getMcastAddress();
		if (content instanceof Message){
			msg = (Message) content;
		} else {
			msg = new Message();
			msg.setObject(content);
		}
		msg.setSrc(new IpAddress(getLocalInetAddress(),PropertiesLoader.getUnicastPort()));
		msg.setDest(new IpAddress(dstInetAddress,PropertiesLoader.getUnicastPort()));
		return msg;
	}
    private InetAddress	getInetAddress(Address dest) {
		return ((IpAddress)dest).getIpAddress();
	}
	/**
	 * Returns an InetAddress from the local Address
	 * @return
	 */
	private InetAddress getLocalInetAddress() {
		return ((IpAddress)getLocalAddress()).getIpAddress();
	}
	
    /* **********************************************
     * 				 MESSAGE RECEPTION
     * ********************************************/
	
	/**
	 * Returns the message. Since it can be wrapped with a sequence number object
	 * we must check if we are performing emulation or real tests
	 * @param msg
	 * @return
	 */
	private Message getReceivedMessage(Message msg) {		
		return msg;
	}

	@Override
	public void setReceiver(Receiver receiver) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object receive(long timeout) throws Exception {
		receive(timeout);
		return null;
	}

	/**
	 * Check whether the message has an identifier. If yes, lookup the MessageListener associated with the
	 * given identifier in the hashtable and dispatch to it. Otherwise just use the main (default) message
	 * listener
	 */
	protected void handleMessage(Message msg) {
		listener.receive(msg);
	}

	public void stop() {
	    Thread tmp=null;
	    if(receiver_thread != null && receiver_thread.isAlive()) {
	        tmp=receiver_thread;
	        receiver_thread=null;
	        tmp.interrupt();
	        try {
	            tmp.join(1000);
	        }
	        catch(Exception ex) {
	        }
	    }
	    receiver_thread=null;
	}

	@Override
	public void send(Message msg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void send(Address dst, Address src, Serializable content) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Sets a listener to messages with a given identifier.
	 * Messages sent with this identifier in their headers will be routed to this listener.
	 * <b>Note: there can be only one listener for one identifier;
	 * if you want to register a different listener to an already registered identifier, then unregister first.</b> 
	 * @param identifier - messages sent on the group with this object will be received by this listener 
	 * @param l - the listener that will get the message
	 */
	public void registerListener(Serializable identifier, MessageListener l) {
	    if(l == null || identifier == null) {
	        if(log.isErrorEnabled()) log.error("message listener or identifier is null");
	        return;
	    }
	    if(listeners.containsKey(identifier)) {
	        if(log.isErrorEnabled()) log.error("listener with identifier=" + identifier +
	                " already exists, choose a different identifier or unregister current listener");
	        // we do not want to overwrite the listener
	        return;
	    }
	    listeners.put(identifier, l);
	}

	/**
	 * Removes a message listener to a given identifier from the message listeners map.
	 * @param identifier - the key to whom we do not want to listen any more
	 */
	public void unregisterListener(Serializable identifier) {
		listeners.remove(identifier);
	}


}