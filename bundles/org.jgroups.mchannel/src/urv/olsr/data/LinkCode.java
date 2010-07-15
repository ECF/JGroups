package urv.olsr.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.jgroups.util.Streamable;

/**
 *  This field specifies information about the link between the
	interface of the sender and the following list of neighbor
	interfaces.  It also specifies information about the status of
	the neighbor.
	Link codes, not known by a node, are silently discarded.
	
	The following four "Link Types" are REQUIRED by OLSR:

     -    UNSPEC_LINK - indicating that no specific information about
          the links is given.

     -    ASYM_LINK - indicating that the links are asymmetric (i.e.,
          the neighbor interface is "heard").

     -    SYM_LINK - indicating that the links are symmetric with the
          interface.

     -    LOST_LINK - indicating that the links have been lost.

   The following three "Neighbor Types" are REQUIRED by OLSR:

     -    SYM_NEIGH - indicating that the neighbors have at least one
          symmetrical link with this node.

     -    MPR_NEIGH - indicating that the neighbors have at least one
          symmetrical link AND have been selected as MPR by the sender.

     -    NOT_NEIGH - indicating that the nodes are either no longer or
          have not yet become symmetric neighbors.

   Note that an implementation should be careful in confusing neither
   Link Type with Neighbor Type nor the constants (confusing SYM_NEIGH
   with SYM_LINK for instance).

   A link code advertising:

          Link Type     == SYM_LINK AND

          Neighbor Type == NOT_NEIGH

   is invalid, and any links advertised as such MUST be silently
   discarded without any processing.

   Likewise a Neighbor Type field advertising a numerical value which is
   not one of the constants SYM_NEIGH, MPR_NEIGH, NOT_NEIGH, is invalid,
   and any links advertised as such MUST be silently discarded without
   any processing.

 * @author Gerard Paris Aixala
 *
 */
public class LinkCode implements Serializable, Streamable{
	
	// CONSTANTS --
	
	// LINK TYPES
	public static final int UNSPEC_LINK = 0; // no specific information about this link
	public static final int ASYM_LINK = 1;   // Asymmetric link
	public static final int SYM_LINK = 2;    // Symmetric link
	public static final int LOST_LINK = 3;   // Lost link
	
	// NEIGHBOR TYPES
	public static final int SYM_NEIGH = 0; // At least 1 symmetrical link
	public static final int MPR_NEIGH = 1; // At least one symmetrical link AND is MPR of the sender
	public static final int NOT_NEIGH = 2; // No longer or not yet symmetric neighbors
	
	private static final int mask2LSB = 3; // 00000011
	
	//	CLASS FIELDS --
	
	private int neighborType;
	private int linkType;
	
	//	CONSTRUCTORS --
	
	public LinkCode(){
		this.neighborType = NOT_NEIGH;
		this.linkType = UNSPEC_LINK;
	}	
	public LinkCode(int neighborType, int linkType){
		this.neighborType = neighborType;
		this.linkType = linkType;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){	
		return new LinkCode(this.neighborType,this.linkType);
	}
	public boolean equals(Object obj){
		return (this.linkType==((LinkCode)obj).linkType && this.neighborType==((LinkCode)obj).neighborType);
	}
	public int hashCode(){
		return (this.neighborType*100)+linkType;	
	}
	public String toString(){
		StringBuffer buff = new StringBuffer();
		buff.append("LT=");
		switch (getLinkType()){
			case UNSPEC_LINK:
				buff.append("UNSPEC_LINK");
				break;
			case ASYM_LINK:
				buff.append("ASYM_LINK");
				break;
			case SYM_LINK:
				buff.append("SYM_LINK");
				break;
			case LOST_LINK:
				buff.append("LOST_LINK");
				break;
			default:
				buff.append("???");
		}
		buff.append(" NT=");
		switch (getNeighborType()){
			case SYM_NEIGH:
				buff.append("SYM_NEIGH");
				break;
			case MPR_NEIGH:
				buff.append("MPR_NEIGH");
				break;
			case NOT_NEIGH:
				buff.append("NOT_NEIGH");
				break;
			default:
				buff.append("???");
		}
		return buff.toString();
	}
	public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
		byte tmp = in.readByte();
		neighborType = ((int)tmp)/4;
		linkType = tmp & mask2LSB;
	}	
	public void writeTo(DataOutputStream out) throws IOException {
		out.writeByte(linkType+neighborType*4); // BYTE: 7 6 5 4    3    2      1  0
												//       0 0 0 0 NeighborType LinkType
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return the linkType
	 */
	public int getLinkType() {
		return linkType;
	}
	/**
	 * @return the neighborType
	 */
	public int getNeighborType() {
		return neighborType;
	}
	/**
	 * @param linkType The linkType to set.
	 */
	public void setLinkType(int linkType) {
		this.linkType = linkType;
	}	
	/**
	 * @param neighborType The neighborType to set.
	 */
	public void setNeighborType(int neighborType) {
		this.neighborType = neighborType;
	}	
}