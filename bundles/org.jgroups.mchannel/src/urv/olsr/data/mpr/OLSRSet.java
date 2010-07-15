package urv.olsr.data.mpr;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import urv.olsr.data.BandwidthUpdatable;
import urv.olsr.data.OLSRNode;

/**
 * This class stores a set of OLSR nodes (no repeated)
 * which can be used for storing MPR and neighbor information
 * 
 * @author Marcel Arrufat Arias
 */
public class OLSRSet extends HashSet<OLSRNode> implements BandwidthUpdatable {

	//	CLASS FIELDS --
	
	Object lock = new Object();
	
	//	CONSTRUCTORS --
	
	public OLSRSet() {}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		synchronized (lock) {
			OLSRSet newSet = new OLSRSet();
			for(OLSRNode node:this){
				newSet.add((OLSRNode)node.clone());
			}			
			return newSet;
		}
	}
	public boolean equals(Object obj){
		if (obj instanceof OLSRSet){
			OLSRSet set2 = (OLSRSet)obj;			
			synchronized (lock) {
				if (set2.size()==this.size()){
					for (OLSRNode n:this){
						if (!set2.contains(n)){
							return false;
						}
					}
					return true; // set2 contains all the nodes in this set
				}
			}
		}		
		return false;
	}	
	public String toString(){
		StringBuffer buff = new StringBuffer();		
		if (this.isEmpty()) {
			buff.append("Set is empty");
		} else {
			buff.append("Set content");
			for (OLSRNode node : this) {
				buff.append(": " + node.toString());
			}
		}		
		buff.append("\n");
		return buff.toString();
	}
	@Override
	public void updateBwOf(OLSRNode node) {
		synchronized (lock) {
			for(OLSRNode currentNode : this){
				if (currentNode.equals(node)) currentNode.updateBandwidth(node);
			}
		}		
	}	
	
	//	PUBLIC METHODS --
	
	/**
	 * Returns a copy of the set, and prevents from concurrent
	 * modifications
	 * @return
	 */
	public Set<OLSRNode> getCopyOfSet(){		
		return (Set<OLSRNode>)clone();		
	}
	/**
	 * Adds all elements to the data set
	 * The data elements cannot be retrieved during
	 * the copy operation
	 * @param list
	 */
	public void setCopyOfSet(List<OLSRNode> list){
		synchronized (lock) {
			this.clear();
			for(OLSRNode node:list){
				
				this.add((OLSRNode)node.clone());
			}
		}
	}
	/**
	 * Adds all elements to the data set
	 * The data elements cannot be retrieved during
	 * the copy operation
	 * @param list
	 */
	public void setCopyOfSet(Set<OLSRNode> set){		
		synchronized (lock) {
			this.clear();
			for(OLSRNode node:set){
				this.add((OLSRNode)node.clone());
			}
		}
	}
}