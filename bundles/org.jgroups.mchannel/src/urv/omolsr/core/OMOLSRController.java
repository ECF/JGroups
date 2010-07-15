package urv.omolsr.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.protocols.OMOLSR;

import urv.olsr.data.OLSRNode;
import urv.omolsr.data.OMOLSRData;
import urv.omolsr.data.OMOLSRNetworkGraph;

/**
 * @author Marcel Arrufat Arias
 * @author Gerard Paris Aixala
 */
public class OMOLSRController {
	
	//	CLASS FIELDS --
	
	/**
	 * The number of hops to be inserted in a new message
	 */
	public static final int INITIAL_HOPS	= 0;
	/**
	 * Reference to the class that contains the information about the multicast
	 * overlay
	 */
	private OMOLSRData data = null;	
	private Handler handler;
	protected final Log log = LogFactory.getLog(this.getClass());
	
	//	CONSTRUCTORS --
	
	public OMOLSRController(OMOLSR omolsr,OLSRNode localNode) {		
		this.data = new OMOLSRData(localNode);
		this.handler = new StandardHandler(omolsr,data,localNode);
	}	
	
	//	PUBLIC METHODS --
	
	/**
	 * Computes a new Mst with the current multicast network graph
	 *
	 */
	public void computeMST(){
		data.computeMST();
	}	
	/**
	 * Registers a new instance of the OMOLSR protocol to the multicast
	 * address
	 * @param groupId The multicast address of the group
	 * @param protocol An instance of the OMOLSR protocol
	 */
	public void registerOmolsrProtocol(OMOLSR protocol){
		protocol.setUnicastHandlerListener(handler);
	}	
	/**
	 * Given its multicast address, unregisters an instance of the OMOLSR
	 * protocol
	 * @param groupId The multicast address of the group
	 */
	public void unregisterOmcastProtocol(String groupId){
		//TODO: nothing special to do here
	}	
	/*//DO NOT REMOVE: may be used later in OMOLSR
	 * 	public void registerBroadcastMinNeighbour(String groupId, int num){
		handler.registerBroadcastMinNeighbour(groupId,num);
	}*/
	public void updateMulticastNetworkGraph(OMOLSRNetworkGraph multicastNetworkGraph) {		
		data.updateOMOLSRNetworkGraph(multicastNetworkGraph);		
	}
}