package urv.olsr.message.generator;

import org.jgroups.Message;

import urv.conf.ApplicationConfig;
import urv.olsr.core.OLSRThread;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.message.HelloMessage;
import urv.olsr.message.OLSRMessageSender;
import urv.olsr.message.OLSRPacket;
import urv.olsr.message.OLSRPacketFactory;
import urv.olsr.message.ValidityTime;

/**
 * This involves transmitting the Link Set, the Neighbor Set and the MPR
   Set.  In principle, a HELLO message serves three independent tasks:

     -    link sensing
     
     -    neighbor detection

     -    MPR selection signaling

   Three tasks are all are based on periodic information exchange within
   a nodes neighborhood, and serve the common purpose of "local topology
   discovery".  A HELLO message is therefore generated based on the
   information stored in the Local Link Set, the Neighbor Set and the
   MPR Set from the local link information base.

   A node must perform link sensing on each interface, in order to
   detect links between the interface and neighbor interfaces.
   Furthermore, a node must advertise its entire symmetric 1-hop
   neighborhood on each interface in order to perform neighbor
   detection.  Hence, for a given interface, a HELLO message will
   contain a list of links on that interface (with associated link
   types), as well as a list of the entire neighborhood (with an
   associated neighbor types).

   The Vtime field is set such that it corresponds to the value of the
   node's NEIGHB_HOLD_TIME parameter.  The Htime field is set such that
   it corresponds to the value of the node's HELLO_INTERVAL parameter
   (see section 18.3).

   The Willingness field is set such that it corresponds to the node's
   willingness to forward traffic on behalf of other nodes (see section
   18.8).  A node MUST advertise the same willingness on all interfaces.
   The lists of addresses declared in a HELLO message is a list of
   neighbor interface addresses computed as follows:

   For each tuple in the Link Set, where L_local_iface_addr is the
   interface where the HELLO is to be transmitted, and where L_time >=
   current time (i.e., not expired), L_neighbor_iface_addr is advertised
   with:

     1    The Link Type set according to the following:

          1.1  if L_SYM_time >= current time (not expired)

                    Link Type = SYM_LINK

          1.2  Otherwise, if L_ASYM_time >= current time (not expired)
               AND

                             L_SYM_time  <  current time (expired)

                    Link Type = ASYM_LINK
                    1.3  Otherwise, if L_ASYM_time < current time (expired) AND

                             L_SYM_time  < current time (expired)

                    Link Type = LOST_LINK

     2    The Neighbor Type is set according to the following:

          2.1  If the main address, corresponding to
               L_neighbor_iface_addr, is included in the MPR set:

                    Neighbor Type = MPR_NEIGH

          2.2  Otherwise, if the main address, corresponding to
               L_neighbor_iface_addr, is included in the neighbor set:

               2.2.1
                    if N_status == SYM

                         Neighbor Type = SYM_NEIGH

               2.2.2
                    Otherwise, if N_status == NOT_SYM
                         Neighbor Type = NOT_NEIGH

   For each tuple in the Neighbor Set, for which no
   L_neighbor_iface_addr from an associated link tuple has been
   advertised by the previous algorithm,  N_neighbor_main_addr is
   advertised with:

     - Link Type = UNSPEC_LINK,

     - Neighbor Type set as described in step 2 above

   For a node with a single OLSR interface, the main address is simply
   the address of the OLSR interface, i.e., for a node with a single
   OLSR interface the main address, corresponding to
   L_neighbor_iface_addr is simply L_neighbor_iface_addr.

   A HELLO message can be partial (e.g., due to message size
   limitations, imposed by the network), the rule being the following,
   on each interface: each link and each neighbor node MUST be cited at
   least once within a predetermined refreshing period,
   REFRESH_INTERVAL.  To keep track of fast connectivity changes, a
   HELLO message must be sent at least every HELLO_INTERVAL period,
   smaller than or equal to REFRESH_INTERVAL.
   
   Notice that for limiting the impact from loss of control messages, it
   is desirable that a message (plus the generic packet header) can fit
   into a single MAC frame.
   
 * @author Gerard Paris Aixala
 *
 */
public class HelloMessageGenerator{

	//	CLASS FIELDS --
	
	private OLSRMessageSender messageSender;
	private NeighborTable neighborTable;
	private OLSRPacketFactory olsrPacketFactory;

	//	CONSTRUCTORS --
	
	public HelloMessageGenerator(OLSRMessageSender messageSender,NeighborTable table, OLSRPacketFactory olsrPacketFactory) {
		this.messageSender = messageSender;
		this.neighborTable = table;
		this.olsrPacketFactory = olsrPacketFactory;
	}
	
	//	PUBLIC METHODS --
	
	public void generateAndSend(){
		HelloMessage helloMsg = neighborTable.createHelloMessage();
		ValidityTime HELLO_MESSAGE_VALIDITY_TIME = new ValidityTime(OLSRThread.HELLO_MESSAGE_VALIDITY_TIME); // TODO define it correctly
		OLSRPacket olsrPacket = olsrPacketFactory.getOlsrPacket(OLSRPacket.HELLO_MESSAGE, HELLO_MESSAGE_VALIDITY_TIME, 1, helloMsg);
		Message msg = new Message(ApplicationConfig.BROADCAST_ADDRESS,null,olsrPacket);
		messageSender.sendControlMessage(msg);
	}
}