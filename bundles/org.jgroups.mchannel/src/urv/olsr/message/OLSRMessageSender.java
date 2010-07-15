package urv.olsr.message;

import org.jgroups.Message;

import urv.olsr.data.OLSRNode;

/**
 * @author Gerard Paris Aixala
 *
 */
public interface OLSRMessageSender {
	
	/**
	 * Sends an OLSR control message.
	 * The implementation of this method must attach the corresponding
	 * OLSRHeader to the message
	 * @param msg
	 */
	public Object sendControlMessage(Message msg);

	/**
	 * Sends an OLSR data message.
	 * The implementation of this method must attach the corresponding
	 * OLSRHeader to the message
	 * @param msg The message including the next hop address as the destination address
	 * @param finalDest The final destination of the message
	 * @param mcast_addr_name 
	 */
	public Object sendDataMessage(Message msg, OLSRNode finalDest, String mcast_addr_name);
}
