package urv.olsr.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.jgroups.util.Streamable;

/**
 * Sequence numbers are used in OLSR with the purpose of discarding
   "old" information, i.e., messages received out of order.  However
   with a limited number of bits for representing sequence numbers,
   wrap-around (that the sequence number is incremented from the maximum
   possible value to zero) will occur.  To prevent this from interfering
   with the operation of the protocol, the following MUST be observed.

   The term MAXVALUE designates in the following the largest possible
   value for a sequence number.

   The sequence number S1 is said to be "greater than" the sequence
   number S2 if:

          S1 > S2 AND S1 - S2 <= MAXVALUE/2 OR

          S2 > S1 AND S2 - S1 > MAXVALUE/2

   Thus when comparing two messages, it is possible - even in the
   presence of wrap-around - to determine which message contains the
   most recent information.
   
 * @author Gerard Paris Aixala
 *
 */
public class SequenceNumber implements Serializable,Streamable,Comparable{

	//	CONSTANTS --
	
	private final static int MAX_SEQUENCE_NUMBER = 65535;
	
	//	CLASS FIELDS --
	
	private int seqNumber;
	
	//	CONSTRUCTORS --
	
	public SequenceNumber(int seq){
		seqNumber = seq;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		return new SequenceNumber(this.seqNumber);
	}
	/**
	 * Returns a negative integer, zero, or a positive integer as this object is
	 * less than, equal to, or greater than the specified object.
	 * @param obj A SequenceNumber object to be compared with this object.
	 * @return
	 */
	public int compareTo(Object obj) {
		int THIS_SEQ_IS_GREATER = 1;
		int EQUAL = 0;
		int THIS_SEQ_IS_LESS = -1;		
		
		SequenceNumber seq2 = (SequenceNumber)obj;
		if (this.equals(seq2)){
			return EQUAL;
		}		
		if (this.seqNumber > seq2.seqNumber){
			if (this.seqNumber - seq2.seqNumber <= MAX_SEQUENCE_NUMBER/2){
				return THIS_SEQ_IS_GREATER;
			}
		}		
		if (this.seqNumber < seq2.seqNumber){
			if (seq2.seqNumber - this.seqNumber > MAX_SEQUENCE_NUMBER/2){
				return THIS_SEQ_IS_GREATER;
			}
		}		
		return THIS_SEQ_IS_LESS;
	}
	public boolean equals(Object obj){
		SequenceNumber seq2 = (SequenceNumber)obj;
		return (this.seqNumber == seq2.seqNumber);
	}
	public int hashCode(){
		return seqNumber;
	}	
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
		seqNumber = (int)(in.readShort() & 0xFFFF);
	}	
	public String toString(){
		return String.valueOf(seqNumber);
	}	
	public void writeTo(DataOutputStream out) throws IOException {
		out.writeShort(seqNumber);
	}	
	
	//	PUBLIC METHODS --
	
	/**
	 * The message sequence number is increased by 1 (one) for each message
	 * originating from the node.
	 */
	public void increase(){
		if (seqNumber < MAX_SEQUENCE_NUMBER){
			seqNumber++;
		}
		else {
			seqNumber = 0;
		}
	}
}