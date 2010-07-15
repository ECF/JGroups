package urv.olsr.data;

/**
 * This interface is implemented by the data structures
 * that can update the bandwidth of its internal nodes.
 *  
 * @author Raul Gracia
 *
 */
public interface BandwidthUpdatable {

	public void updateBwOf (OLSRNode node);
	
}