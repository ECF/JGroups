package urv.olsr.core;

import urv.olsr.data.OLSRNode;
import urv.olsr.data.duplicate.DuplicateTable;
import urv.olsr.data.mpr.MprSelectorSet;
import urv.olsr.data.mpr.MprSet;
import urv.olsr.data.neighbour.NeighborTable;
import urv.olsr.data.topology.TopologyInformationBaseTable;
import urv.olsr.mcast.TopologyInformationSender;
import urv.olsr.mcast.MulticastGroupsTable;
import urv.olsr.mcast.MulticastNetworkGraphComputationController;
import urv.olsr.message.OLSRMessageSender;
import urv.olsr.message.OLSRPacketFactory;
import urv.olsr.message.generator.HelloMessageGenerator;
import urv.olsr.message.generator.TcMessageGenerator;

/**
 * This class is used to execute the OLSR tasks periodically
 * 
 * @author Gerard Paris Aixala
 *
 */
public class OLSRThread extends Thread {

	//	CONSTANTS --
	
	private static final int BASE_TIME = 100; // 100 ms
	private static final int HELLO_INTERVAL 	= BASE_TIME*20;
	private static final int TC_INTERVAL 		= BASE_TIME*50;
	private static final int INTERVAL_LCM 		= BASE_TIME*90;
	public static final int TC_MESSAGE_VALIDITY_TIME  = (4*TC_INTERVAL)/1000; //Time in seconds
	public static final int HELLO_MESSAGE_VALIDITY_TIME = (4*HELLO_INTERVAL)/1000; //Time in seconds

	//	CLASS FIELDS --
	
	private OLSRMessageSender olsrMessageSender;
	private NeighborTable neighborTable;
	private DuplicateTable duplicateTable;
	private TopologyInformationBaseTable topologyTable;
	private MulticastGroupsTable multicastGroupsTable;
	private MprComputationController mprComputationController;
	private RoutingTableComputationController routingTableComputationController;
	private MulticastNetworkGraphComputationController multicastNetworkGraphComputationController;
	// Message Generators
	private HelloMessageGenerator helloMessageGenerator;
	private TcMessageGenerator tcMessageGenerator;
	private TopologyInformationSender controllerUpper;
	private boolean extraTCMessage = false;

	//	CONSTRUCTORS --
	
	public OLSRThread(OLSRMessageSender sender, NeighborTable neighborTable, MprComputationController mprComputationController,
			RoutingTableComputationController routingTableComputationController, MprSelectorSet mprSelectorSet,
			OLSRPacketFactory olsrPacketFactory, TopologyInformationBaseTable topologyTable, DuplicateTable duplicateTable,
			TopologyInformationSender controllerUpper, MulticastNetworkGraphComputationController multicastNetworkGraphComputationController,
			MulticastGroupsTable multicastGroupsTable, OLSRNode localNode){
		this.olsrMessageSender = sender;
		this.neighborTable = neighborTable;
		this.mprComputationController = mprComputationController;
		this.routingTableComputationController = routingTableComputationController;
		this.topologyTable = topologyTable;
		this.duplicateTable = duplicateTable;
		this.helloMessageGenerator = new HelloMessageGenerator(sender, neighborTable, olsrPacketFactory);
		this.tcMessageGenerator  = new TcMessageGenerator(sender, mprSelectorSet, olsrPacketFactory, multicastGroupsTable, localNode);
		this.controllerUpper = controllerUpper;
		this.multicastNetworkGraphComputationController = multicastNetworkGraphComputationController;
		this.multicastGroupsTable = multicastGroupsTable;
	}
	
	//	OVERRIDDEN METHODS --
	
	public void run(){
		int countHello = 0;
		int countTc = 0;
		int count = 0;

		long timeBefore = System.currentTimeMillis();
		long timeNow;
		long diff;

		while(true){
			try {
				// Wait the BASE_TIME defined interval to execute the process again
				Thread.sleep(BASE_TIME);
				timeNow = System.currentTimeMillis();
				diff = timeNow-timeBefore;

				// Recalculation of the time elapsed between executions
				count += diff;
				countHello += diff;
				countTc += diff;
				timeBefore = timeNow;

				neighborTable.decreaseTimeAndProcessEntries(diff);
				duplicateTable.decreaseTimeAndProcessEntries(diff);
				topologyTable.decreaseTimeAndProcessEntries(diff);
				
				// MPR recomputation
				if (neighborTable.isRecomputeMprFlag()){
					//If the computation was ok, there is no need to recompute again
					if (mprComputationController.computeNewMprSet()==true){
						neighborTable.setRecomputeMprFlag(false);
						MprSet mprSet = mprComputationController.getMprSet();
						neighborTable.onMPRSetChange(mprSet);
					}
				}
				// Routing Table recomputation
				if (neighborTable.isNeighborTableChangedFlag() || topologyTable.isTopologyTableChangedFlag()){
					
					neighborTable.setNeighborTableChangedFlag(false);
					topologyTable.setTopologyTableChangedFlag(false);
					// MulticastNetworkGraph recomputation
					multicastGroupsTable.setMulticastGroupsTableChangedFlag(false);
					multicastNetworkGraphComputationController.computeNewMulticastNetworkGraph();
					// Report event up to the stacks (networkGraph)
					controllerUpper.sendTopologyInformationEvent();
					routingTableComputationController.computeNewRoutingTable();
				}
				if (multicastGroupsTable.isMulticastGroupsTableChangedFlag()){					
					multicastGroupsTable.setMulticastGroupsTableChangedFlag(false);
					multicastNetworkGraphComputationController.computeNewMulticastNetworkGraph();
					// Report event up to the stacks (networkGraph)
					controllerUpper.sendTopologyInformationEvent();
				}
				// Generation of HELLO Messages
				if (countHello >= HELLO_INTERVAL){
					helloMessageGenerator.generateAndSend();
					countHello = countHello % HELLO_INTERVAL;
				}
				// Generation of TC messages
				if (countTc >= TC_INTERVAL){
					tcMessageGenerator.generateAndSend();
					countTc = countTc % TC_INTERVAL;
					extraTCMessage = false;
				} else if (extraTCMessage){
					tcMessageGenerator.generateAndSend();
					extraTCMessage = false;
				}
				if (count >= INTERVAL_LCM) {
					// As Log is a Singleton class... maybe we only need a node to invoke printLoggables()
					// Solution: printLoggables is now invoked from another thread!!!!!
					//Log.getInstance().printLoggables();
					count = count % INTERVAL_LCM;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	//	ACCESS METHODS --
	
	public void setExtraTCMessage(boolean value){
		extraTCMessage  = value;
	}
}