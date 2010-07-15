package urv.olsr.mcast;

/**
 * Interface that implement the classes that need to pass topology 
 * information events to the above layer.
 * 
 * @author Gerard Paris Aixala
 * @author Raul Gracia Tinedo
 *
 */
public interface TopologyInformationSender {
	
	/**
	 * Method defined in order to send TopologyEvents to the
	 * above layer.
	 * 
	 * @see TopologyEvent
	 */
	public void sendTopologyInformationEvent();
	
}