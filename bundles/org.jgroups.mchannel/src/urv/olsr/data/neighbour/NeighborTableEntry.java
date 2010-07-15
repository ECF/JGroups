package urv.olsr.data.neighbour;

import urv.olsr.data.LinkCode;
import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.OLSRSet;

/**
 * Entry of the NeighborTable. It must contain information 
 * about the node itself, link status and node neighbors
 * 
 * @author Marcel Arrufat Arias
 */
public class NeighborTableEntry implements Cloneable{
	
	//	CLASS FIELDS --

	private OLSRNode neighbor;
	private LinkCode linkCode;
	private OLSRSet neighborsOfNeighbors;
	
	//	CONTRUCTORS --
	
	public NeighborTableEntry(OLSRNode neighbor, LinkCode linkStatus,OLSRSet neighborsOfNeighbors) {	
		this.neighbor = neighbor;
		this.linkCode = linkStatus;
		this.neighborsOfNeighbors = neighborsOfNeighbors;
	}	
	
	//	OVERRIDDEN METHODS --
	
	/**
	 * Checks wether two entries are equal
	 */
	public boolean equals(Object obj){
		NeighborTableEntry entry = (NeighborTableEntry)obj;
		return neighbor.equals(entry.neighbor) && linkCode.equals(entry.linkCode) && neighborsOfNeighbors.equals(entry.neighborsOfNeighbors);
	}
	public String toString(){
		return "("+linkCode.toString()+") "+neighborsOfNeighbors.toString(); 
	}
	
	//	ACCESS METHODS --
	
	/**
	 * @return Returns the linkCode.
	 */
	public LinkCode getLinkCode() {
		return linkCode;
	}
	/**
	 * @return Returns the neighbor.
	 */
	public OLSRNode getNeighbor() {
		return neighbor;
	}
	/**
	 * @return Returns the neighborsOfNeighbors.
	 */
	public OLSRSet getNeighborsOfNeighbors() {
		return neighborsOfNeighbors;
	}
	public void setNeighbor(OLSRNode neighbor) {
		this.neighbor = neighbor;
	}
	public void setLinkCode(LinkCode linkCode) {
		this.linkCode = linkCode;
	}
	public void setNeighborsOfNeighbors(OLSRSet neighborsOfNeighbors) {
		this.neighborsOfNeighbors = neighborsOfNeighbors;
	}
}