package urv.olsr.data.topology;

import urv.olsr.data.OLSRNode;

/**
 * This class represents a pair of OLSR nodes, which will be used for storing
 * topology information in the TopologyInformationBaseTable
 * 
 * @author Marcel Arrufat Arias
 */
public class OLSRNodePair {
	
	//	CLASS FIELDS --

	private OLSRNode originator;
	private OLSRNode advertised;
	
	//	CONSTRUCTORS --

	public OLSRNodePair(OLSRNode originator,OLSRNode advertised) {
		super();
		this.originator = originator;
		this.advertised = advertised;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){
		OLSRNode orig = (OLSRNode) this.originator.clone();
		OLSRNode advertised = (OLSRNode) this.advertised.clone();		
		return new OLSRNodePair(orig,advertised);
	}
	public boolean equals(Object obj){
		OLSRNodePair pair = (OLSRNodePair)obj;
		return this.originator.equals(pair.originator) && this.advertised.equals(pair.advertised);
	}	
	public String toString(){		
		return originator.toString()+"-"+advertised.toString();
	}
	//TODO Address hashCode's are 32 bit length.
	public int hashCode(){
		//Trying to avoid collisions when "originator" is "advertised" and viceversa
		int hash = originator.hashCode()+(37*advertised.hashCode());
		return hash;
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the advertised.
	 */
	public OLSRNode getAdvertised() {
		return advertised;
	}
	/**
	 * @return Returns the originator.
	 */
	public OLSRNode getOriginator() {
		return originator;
	}	
	/**
	 * @param advertised The advertised to set.
	 */
	public void setAdvertised(OLSRNode advertised) {
		this.advertised = advertised;
	}	
	/**
	 * @param originator The originator to set.
	 */
	public void setOriginator(OLSRNode originator) {
		this.originator = originator;
	}
}