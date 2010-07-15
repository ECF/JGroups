package urv.olsr.data.routing;

import urv.olsr.data.OLSRNode;

/**
 * Data unit to store the route between to neighbor nodes.
 * 
 * @author Marcel Arrufat Arias
 */
public class RoutingTableEntry {

	//	CLASS FIELDS --
	
	private OLSRNode destAddr;
	private OLSRNode nextAddr;
	private int	hops;

	//	CONSTRUCTORS --
	
	public RoutingTableEntry() {
		super();
	}
	/**
	 * @param destAddr
	 * @param nextAddr
	 * @param hops
	 */
	public RoutingTableEntry(OLSRNode destAddr, OLSRNode nextAddr, int hops) {
		this.destAddr = destAddr;
		this.nextAddr = nextAddr;
		this.hops = hops;
	}
	
	//	OVERRIDDEN METHODS --
	
	public Object clone(){		
		return new RoutingTableEntry((OLSRNode)destAddr.clone(),(OLSRNode)nextAddr.clone(),hops);
	}
	
	//	ACESS METHODS --
	
	/**
	 * @return Returns the destAddr.
	 */
	public OLSRNode getDestAddr() {
		return destAddr;
	}
	/**
	 * @return Returns the hops.
	 */
	public int getHops() {
		return hops;
	}	
	/**
	 * @return Returns the nextAddr.
	 */
	public OLSRNode getNextAddr() {
		return nextAddr;
	}
}