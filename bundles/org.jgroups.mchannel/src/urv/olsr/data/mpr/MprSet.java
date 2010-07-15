package urv.olsr.data.mpr;

import urv.log.Loggable;
import urv.olsr.data.OLSRNode;

/**
 * This class holds information about the multi-point relay
 * nodes (MPR) of the current node
 * 
 * @author Marcel Arrufat Arias
 */
public class MprSet extends OLSRSet implements Loggable{

	//	CLASS FIELDS --
	
	private OLSRNode localNode;
	
	//	CONSTRUCTORS --
	
	public MprSet(OLSRNode localNode) {
		super();
		this.localNode = localNode;
	}
	
	//	OVERRIDDEN METHODS --
	
	public int hashCode(){
		return localNode.hashCode();
	}	
	public String toString(){
		return "MPR_SET["+localNode+"]"+super.toString();
	}
}