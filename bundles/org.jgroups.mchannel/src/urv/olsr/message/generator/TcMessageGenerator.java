package urv.olsr.message.generator;

import org.jgroups.Message;

import urv.conf.ApplicationConfig;
import urv.olsr.core.OLSRThread;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.MprSelectorSet;
import urv.olsr.mcast.MulticastGroupsTable;
import urv.olsr.message.OLSRMessageSender;
import urv.olsr.message.OLSRPacket;
import urv.olsr.message.OLSRPacketFactory;
import urv.olsr.message.TcMessage;
import urv.olsr.message.ValidityTime;

/**
 * In order to build the topology information base, each node, which has
   been selected as MPR, broadcasts Topology Control (TC) messages.  TC
   messages are flooded to all nodes in the network and take advantage
   of MPRs.  MPRs enable a better scalability in the distribution of
   topology information [1].

   The list of addresses can be partial in each TC message (e.g., due to
   message size limitations, imposed by the network), but parsing of all
   TC messages describing the advertised link set of a node MUST be
   complete within a certain refreshing period (TC_INTERVAL).  The
   information diffused in the network by these TC messages will help
   each node calculate its routing table.

   When the advertised link set of a node becomes empty, this node
   SHOULD still send (empty) TC-messages during the a duration equal to
   the "validity time" (typically, this will be equal to TOP_HOLD_TIME)
   of its previously emitted TC-messages, in order to invalidate the
   previous TC-messages.  It SHOULD then stop sending TC-messages until
   some node is inserted in its advertised link set.

   A node MAY transmit additional TC-messages to increase its
   reactiveness to link failures.  When a change to the MPR selector set
   is detected and this change can be attributed to a link failure, a
   TC-message SHOULD be transmitted after an interval shorter than
   TC_INTERVAL.
   
 * @author Gerard Paris Aixala
 *
 */
public class TcMessageGenerator{
	
	//	CLASS FIELDS --

	private OLSRMessageSender messageSender;
	private MprSelectorSet mprSelectorSet;
	private OLSRPacketFactory olsrPacketFactory;
	private MulticastGroupsTable multicastGroupsTable;
	private OLSRNode localNode;
	
	//	CONSTRUCTORS --

	public TcMessageGenerator(OLSRMessageSender messageSender,MprSelectorSet mprSelectorSet, OLSRPacketFactory olsrPacketFactory,
			MulticastGroupsTable multicastGroupsTable,OLSRNode localNode) {
		this.messageSender = messageSender;
		this.mprSelectorSet = mprSelectorSet;
		this.olsrPacketFactory = olsrPacketFactory;
		this.multicastGroupsTable = multicastGroupsTable;
		this.localNode = localNode;
	}
	
	//	PUBLIC METHODS --
	
	public void generateAndSend(){
		TcMessage tcMsg = mprSelectorSet.createTcMessage();
		tcMsg.setJoinedMulticastGroups(multicastGroupsTable.getJoinedMulticastGroups(localNode));
		ValidityTime TC_MESSAGE_VALIDITY_TIME = new ValidityTime(OLSRThread.TC_MESSAGE_VALIDITY_TIME); // TODO define it correctly
		OLSRPacket olsrPacket = olsrPacketFactory.getOlsrPacket(OLSRPacket.TC_MESSAGE, TC_MESSAGE_VALIDITY_TIME, 32, tcMsg);
		Message msg = new Message(ApplicationConfig.BROADCAST_ADDRESS,null,olsrPacket);
		messageSender.sendControlMessage(msg);
	}
}