package urv.olsr.util;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.mpr.OLSRSet;

/**
 * @author Marcel Arrufat Arias
 */
public class Util {
	
	//	STATIC METHODS --

	/**
	 * Returns a copy of the list of nodes
	 */
	public static OLSRSet copyNodeSet(OLSRSet originalSet){		
		OLSRSet clonedSet = new OLSRSet();		
		for (OLSRNode node:originalSet){
			clonedSet.add((OLSRNode)node.clone());
		}
		return clonedSet;
	}
	/**
	 * This method will return a list of the addresses of the neighbors
	 * in the group (virtual neighbors)
	 * 
	 * @param realNeighbors
	 * @return group neighbors
	 */
	public static List<InetAddress> getAddressList(OLSRSet realNeighbors) {
		LinkedList<InetAddress> list = new LinkedList<InetAddress>();
		for (OLSRNode node:realNeighbors){
			list.add(node.getAddress());			
		}
		return list;
	}
}