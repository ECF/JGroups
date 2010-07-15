package urv.machannel;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.MessageListener;
import org.jgroups.Receiver;
import org.jgroups.Transport;
import org.jgroups.View;
import org.jgroups.blocks.PullPushAdapter;
import org.jgroups.stack.Protocol;

import urv.olsr.data.OLSRNode;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * This interface defines the methods that provides
 * our Topology Aware Multicast Channel
 * 
 * @author Marcel Arrufat Arias
 * @author Raúl Gracia Tinedo
 */
public interface MChannel extends Transport {
	/**
	 * Returns the Address of the Local Node
	 * 
	 * @return local Address
	 */
	public Address getLocalAddress();
	/**
	 * This method retrieves the name of the channel
	 * 
	 * @return MChannelName
	 */
	public String getChannelName();
	/**
	 * Return the InetAddresses of the group members
	 * 
	 * @return addressesOfGroupMembers
	 */
	public List<InetAddress> getInetAddressesOfGroupMembers();
	/**
	 * Retrieves the NetworkGraph with the topology below us
	 * 
	 * @return Topology Graph
	 */	
	public NetworkGraph<OLSRNode,Weight> getNetworkGraph();
	/**
	 * Returns a view with the members of the current group
	 * 
	 * @see View
	 * @return Members
	 */
	public View getView();	
	
	abstract void setView( View v);
	/**
	 * Stops the channel
	 */
	public void close();
	/**
	 * Sends the message to the destination of the messages. As the normal Channel,
	 * if the destination is a multicast address, the message is sent to all members
	 * of this group.
	 */
	public void send(Message msg);	
	/**
	 * Sends an unicast message to the selected member.
	 */
	public void send(Address dst, Address src, Serializable content);
	/**
	 * Send a message to all neighbours (1 hop) of the current group
	 */
	public void sendToNeighbors(Serializable content);
	/**
	 * Add a Message listener in the transport layer
	 * 
	 * @see PullPushAdapter
	 * @param listener
	 */
	public void unregisterListener(Serializable identifier);
	/**
	 * Removes a Message listener from the transport layer
	 * 
	 * @see PullPushAdapter
	 * @param listener
	 */
	public void registerListener(Serializable identifier, MessageListener messageListener);
	/**
	 * Sets the messages receiver as PullPushAdapapter is deprecated
	 * @param receiver
	 */
	public void setReceiver( Receiver receiver );
	
}