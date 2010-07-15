package urv.olsr.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.jgroups.util.Streamable;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;

/**
 * OLSR communicates using a unified packet format for all data related
   to the protocol.  The purpose of this is to facilitate extensibility
   of the protocol without breaking backwards compatibility.  This also
   provides an easy way of piggybacking different "types" of information
   into a single transmission, and thus for a given implementation to
   optimize towards utilizing the maximal frame-size, provided by the
   network.  These packets are embedded in UDP datagrams for
   transmission over the network.  The present document is presented
   with IPv4 addresses.  Considerations regarding IPv6 are given in
   section 17.

   Each packet encapsulates one or more messages.  The messages share a
   common header format, which enables nodes to correctly accept and (if
   applicable) retransmit messages of an unknown type.

   Messages can be flooded onto the entire network, or flooding can be
   limited to nodes within a diameter (in terms of number of hops) from
   the originator of the message.  Thus transmitting a message to the
   neighborhood of a node is just a special case of flooding.  When
   flooding any control message, duplicate retransmissions will be
   eliminated locally (i.e., each node maintains a duplicate set to
   prevent transmitting the same OLSR control message twice) and
   minimized in the entire network through the usage of MPRs as
   described in later sections.

   Furthermore, a node can examine the header of a message to obtain
   information on the distance (in terms of number of hops) to the
   originator of the message.  This feature may be useful in situations
   where, e.g., the time information from a received control messages
   stored in a node depends on the distance to the originator.
   
 * @author Gerard Paris Aixala
 *
 */
public class OLSRPacket implements Streamable, Serializable{
	
	//	CLASS FIELDS --
	
	/* Considerations:
	 * - Each OLSR packet will contain a single message, 
	 *   so we avoid packet length and packet sequence number.
	 */
	public static final int HELLO_MESSAGE = 1;
	public static final int TC_MESSAGE = 2;	
	private int messageType;
	private ValidityTime vTime;
	private OLSRNode originator;
	private int ttl;
	private int hopCount;
	private SequenceNumber messageSequenceNumber;	
	private OLSRMessage content;	
	
	//	CONSTRUCTORS --
	
	public OLSRPacket(){
		// Needed for Streamable
	}
	public OLSRPacket(int messageType, OLSRMessage content){
		super();
		this.messageType = messageType;
		this.content = content;
	}	
	/**
	 * @param messageType
	 * @param time
	 * @param originator
	 * @param ttl
	 * @param hopCount
	 * @param messageSequenceNumber
	 * @param content
	 */
	public OLSRPacket(int messageType, ValidityTime time, OLSRNode originator, int ttl, int hopCount, SequenceNumber messageSequenceNumber, OLSRMessage content) {
		super();
		this.messageType = messageType;
		vTime = time;
		this.originator = originator;
		this.ttl = ttl;
		this.hopCount = hopCount;
		this.messageSequenceNumber = messageSequenceNumber;
		this.content = content;
	}
	
	//	OVERRIDDEN METHODS --
	
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
		messageType = (int)in.readByte();
		
		vTime = new ValidityTime(0.0); 
		vTime.readFrom(in);
		
		originator = new OLSRNode();
		originator.readFrom(in);
		
		ttl = (int)in.readByte();
		hopCount = (int)in.readByte();
		
		messageSequenceNumber = new SequenceNumber(0);
		messageSequenceNumber.readFrom(in);
		
		if (messageType == HELLO_MESSAGE){
			content = new HelloMessage();
		}
		else if (messageType == TC_MESSAGE){
			content = new TcMessage();
		}
		content.readFrom(in);		
	}
	/**
	 * @param hopCount the hopCount to set
	 */
	public void setHopCount(int hopCount) {
		this.hopCount = hopCount;
	}
	/**
	 * @param ttl the ttl to set
	 */
	public void setTtl(int ttl) {
		this.ttl = ttl;
	}
	public void writeTo(DataOutputStream out) throws IOException {
		out.writeByte(messageType);
		vTime.writeTo(out);
		originator.writeTo(out);
		out.writeByte(ttl);
		out.writeByte(hopCount);
		messageSequenceNumber.writeTo(out);
		content.writeTo(out);
	}
	
	//	ACCESS METHODS --
	
	/**
	 * Decreses the ttl by one
	 *
	 */
	public void decreaseTtl() {
		ttl--;
	}	
	/**
	 * @return the content
	 */
	public OLSRMessage getContent() {
		return content;
	}
	/**
	 * @return the hopCount
	 */
	public int getHopCount() {
		return hopCount;
	}
	/**
	 * @return the messageSequenceNumber
	 */
	public SequenceNumber getMessageSequenceNumber() {
		return messageSequenceNumber;
	}	
	/**
	 * @return the messageType
	 */
	public int getMessageType() {
		return messageType;
	}
	/**
	 * @return the originator
	 */
	public OLSRNode getOriginator() {
		return originator;
	}
	/**
	 * @return the ttl
	 */
	public int getTtl() {
		return ttl;
	}	
	/**
	 * @return the vTime
	 */
	public ValidityTime getVTime() {
		return vTime;
	}
	/**
	 * Increases hop count by one
	 */
	public void increaseHopCount() {
		hopCount++;
	}
}