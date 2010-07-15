package urv.olsr.message;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * This class provides helper methods to create objects of the OLSRPacket class.
 * These objects include the corresponding sequence number and the originator
 * node.
 * 
 * @author Gerard Paris Aixala
 *
 */
public class OLSRPacketFactory {
	
	//	CLASS FIELDS --

	private OLSRNode localNode;
	private SequenceNumber messageSequenceNumber;
	
	//	CONSTRUCTORS --
	
	public OLSRPacketFactory(OLSRNode localNode){
		this.localNode = localNode;
		this.messageSequenceNumber = new SequenceNumber(0);
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Obtains an OLSRPacket with the specified type,time,ttl and content;
	 * with the local node as originator, the hop-count set to 0, and the
	 * corresponding message sequence number.
	 * @param messageType
	 * @param time
	 * @param ttl
	 * @param content
	 * @return
	 */
	public OLSRPacket getOlsrPacket(int messageType,ValidityTime time,int ttl,OLSRMessage content){		
		OLSRPacket packet = new OLSRPacket(messageType,time,localNode,ttl,0,
			(SequenceNumber)messageSequenceNumber.clone(),content);
		messageSequenceNumber.increase();
		return packet;
	}	
	
	//	ACCESS METHODS --
	
	/**
	 * @return the localNode
	 */
	public OLSRNode getLocalNode() {
		return localNode;
	}
}