package urv.olsr.message;

import org.jgroups.Message;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.routing.RoutingTable;
import urv.util.graph.NetworkGraph;
import urv.util.graph.Weight;

/**
 * @author Gerard Paris Aixala
 *
 */
public interface OLSRMessageUpper {
	
	public Object passUpdateEvent(NetworkGraph<OLSRNode, Weight> mcastNetworkGraph, RoutingTable routingTable);
	/**
	 * Sends an OLSR data message up to one stack from the 
	 * OLSRController singleton 
	 * @param msg
	 */
	public Object sendMessageUp(Message msg);
}