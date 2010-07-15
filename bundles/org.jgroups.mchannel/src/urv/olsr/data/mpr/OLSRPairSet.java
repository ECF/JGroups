package urv.olsr.data.mpr;

import java.util.HashSet;
import java.util.Set;

import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.topology.OLSRNodePair;

/**
 * This class contains a set of OLSRPairs, which are a tuple of
 * <OLSRNode,OLSRNode>
 * 
 * @author Marcel Arrufat Arias
 */
public class OLSRPairSet extends HashSet<OLSRNodePair> implements BandwidthUpdatable{
	
	//	CLASS FIELDS --
	
	private Object lock = new Object();
	
	//	CONSTRUCTORS --
	
	public OLSRPairSet() {
		super();
	}	
	
	//	OVERRIDDEN METHODS --
	
	public boolean add(OLSRNodePair node){		
		boolean value; 
		synchronized (lock) {		
			value = super.add(node);
		}
		return value;		
	}	
	public void clear(){	
		synchronized (lock) {
			super.clear();
		}
	}	
	/**
	 * Creates a new instance of the Set
	 */
	public Object clone(){		
		OLSRPairSet set = new OLSRPairSet();
		synchronized (lock) {
			for(OLSRNodePair node:this){
				set.add((OLSRNodePair)node.clone());
			}
		}
		return set;
	}	
	public boolean contains(Object node){		
		boolean value; 
		synchronized (lock) {
			value = super.contains(node);
		}
		return value;
	}
	public void updateBwOf(OLSRNode node){	
		synchronized (lock) {
			for (OLSRNodePair pair : this){
				if (pair.getOriginator().equals(node)){
					pair.getOriginator().updateBandwidth(node);
				}else if (pair.getAdvertised().equals(node)){
					pair.getAdvertised().updateBandwidth(node);
				}
			}
		}
	}
	
	//	PUBLIC METHODS --
	
	/**
	 * Adds all elements to the data set
	 * The data elements cannot be retrieved during
	 * the copy operation
	 * @param list
	 */
	public void setCopyOfSet(Set<OLSRNodePair> set){
		
		synchronized (lock) {
			super.clear();
			for(OLSRNodePair node:set){
				this.add((OLSRNodePair)node.clone());
			}
		}
	}
}