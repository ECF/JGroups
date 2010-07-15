package urv.olsr.data.mpr;

import java.util.Set;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.SequenceNumber;
import urv.olsr.message.TcMessage;

/**
 * A node records a set of MPR-selector tuples (MS_main_addr, MS_time),
   describing the neighbors which have selected this node as a MPR.
   MS_main_addr is the main address of a node, which has selected this
   node as MPR.  MS_time specifies the time at which the tuple expires
   and *MUST* be removed.

   In a node, the set of MPR-selector tuples are denoted the "MPR
   Selector Set".

 * @author Gerard Paris Aixala
 */

public class MprSelectorSet extends OLSRSet{

	//	CLASS FIELDS --
	
	private static final long serialVersionUID = 1L;
	private SequenceNumber ansn;	
	
	//	CONSTRUCTORS --
	
	public MprSelectorSet(OLSRNode localNode) {
		super();
		ansn = new SequenceNumber(0);
	}	
	
	//	OVERRIDDEN METHODS --
	
	public void setCopyOfSet(Set<OLSRNode> set){
		super.setCopyOfSet(set);
		ansn.increase();
	}
	
	//	PUBLIC METHODS --
	
	public TcMessage createTcMessage(){		
		TcMessage tm = new TcMessage((SequenceNumber) ansn.clone(),(OLSRSet)this.clone());
		return tm;
	}
}