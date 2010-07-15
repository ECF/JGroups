package urv.olsr.data.neighbour;

import urv.olsr.data.mpr.OLSRPairSet;

/**
 * A node records a set of "2-hop tuples" (N_neighbor_main_addr,
   N_2hop_addr, N_time), describing symmetric (and, since MPR links by
   definition are also symmetric, thereby also MPR) links between its
   neighbors and the symmetric 2-hop neighborhood.  N_neighbor_main_addr
   is the main address of a neighbor, N_2hop_addr is the main address of
   a 2-hop neighbor with a symmetric link to N_neighbor_main_addr, and
   N_time specifies the time at which the tuple expires and *MUST* be
   removed.

   In a node, the set of 2-hop tuples are denoted the "2-hop Neighbor
   Set".
   
 * @author Marcel Arrufat Arias
 */
public class NeighborsOfNeighborsSet extends OLSRPairSet{
	
	//	CONSTRUCTORS --
	
	public NeighborsOfNeighborsSet() {
		super();
	}
}