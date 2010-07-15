package urv.conf;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jgroups.Address;
import org.jgroups.stack.IpAddress;

/**
 * This class is used to set the protocol stack into the application.
 * 
 * @author Marcel Arrufat Arias
 */
public class ApplicationConfig {

	//	CONSTANTS --
	
	public static final String OMOLSR_PROTOCOL = "OMOLSR";
	public static final String SMCAST_PROTOCOL = "SMCAST";
	//This attribute is used to enable the MPR selection instead the broadcast when a node has more
	//than this number of neighbors
	public static final int LOCAL_BROADCAST_MIN_NEIGHBOURS = 2;
	//Initial value for the emulated Ips (i.e the first node will have assigned this address. Other nodes
	//will have subsequent addresses. Only valid till 254 nodes in the emulation
	public static byte[] emulatedIPs = new byte[]{(byte)192,(byte)168,(byte)145,(byte)1};
	public static Address BROADCAST_ADDRESS;

	static{
		InetAddress broadcastInetAddress = null;
		try {
			broadcastInetAddress = InetAddress.getByName("255.255.255.255");
			} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		BROADCAST_ADDRESS = new IpAddress(broadcastInetAddress,PropertiesLoader.getUnicastPort());
	}	
	
	//	STATIC METHODS --
	
	/**
	 * Protocol stack WITHOUT multicast protocol
	 */
	public static String getProtocolStackConfig(int nodeNumber,int port){
		if (PropertiesLoader.isEmulated()){
			return getEMU_UDP(nodeNumber, port)+":"
			+getOLSR(null)+":"+getFC()+":"+getFRAG2();
		} else {
			return getJOLSR_UDP(port)+":"+
				getOLSR(null)+":"+getFC()+":"+getFRAG2();
		}
	}
	/**
	 * Protocol stack WITH multicast protocol
	 */
	public static String getProtocolStackConfig(int nodeNumber, int port, InetAddress group) {
		StringBuffer stack = new StringBuffer();
		
		if (PropertiesLoader.isEmulated()){			
			stack.append(getEMU_UDP(nodeNumber, port));	
			if (PropertiesLoader.isDynamicCredit() &&
				PropertiesLoader.isThroughputOptimizationNetworkSelfKnowledgementEnabled()){
				stack.append(":"+getBW_CALC());
			}
			stack.append(":"+getOLSR(group));				
			if (PropertiesLoader.isReliabilityEnabled()){
				stack.append(":"+getReliability(nodeNumber));
			}
			stack.append(":"+getMulticastProtocol(group));
			stack.append(":"+getFC());
			stack.append(":"+getFRAG2());
		}else {
			stack.append(getJOLSR_UDP(port));
			if (PropertiesLoader.isDynamicCredit() &&
				PropertiesLoader.isThroughputOptimizationNetworkSelfKnowledgementEnabled()){
				stack.append(":"+getBW_CALC());
			}			
			stack.append(":"+getOLSR(group));				
			if (PropertiesLoader.isReliabilityEnabled()){
				stack.append(":"+getReliability(nodeNumber));
			}
			stack.append(":"+getMulticastProtocol(group));
			stack.append(":"+getFC());
			stack.append(":"+getFRAG2());
		}
		return stack.toString();
	}
	
	//	PRIVATE METODS --
	
	private static String getBW_CALC (){
		StringBuffer buff = new StringBuffer();
		buff.append("BW_CALC("
		+"info_millis=2000;"
		+"minimumCapacityInBytes=180000;"
		+"minimumCapacityInMessages=100)");
		return buff.toString();
	}
	
	/**
	 * Protocol used in the emulation mode. The aim of this protocol is collect all the transmitted packets
	 * to control the whole application behaviour.
	 * @param nodeNumber
	 * @param port
	 * @return emulation transport protocol
	 */
	private static String getEMU_UDP(int nodeNumber, int port){
		StringBuffer buff = new StringBuffer();
		String emu_node_id = nodeNumber+"";
		String emu_port = port + "";

		buff.append("EMU_UDP("
		+ "mcast_send_buf_size=640000;discard_incompatible_packets=true;ucast_recv_buf_size=20000000;"
		+ "loopback=true;mcast_recv_buf_size=25000000;max_bundle_size=64000;max_bundle_timeout=30;"
		+ "ucast_send_buf_size=640000;tos=16;enable_bundling=false;ip_ttl=32;"
		+ "port_range=1000;"
		+ "emu_node_id="+emu_node_id+";"
		+ "emu_port="+emu_port+")");

		return buff.toString();
	}	
	/**
	 * Protocol used to fragment big amounts of information in smaller packets
	 */
	private static String getFC (){
		//Note: FC.min_credits must to be > than FRAG2.frag_size
		return "FC(max_credits=150000;lowest_max_credits=110000;min_credits=60000;min_threshold=0.25)";
	}
	/**
	 * Protocol used to fragment big amounts of information in smaller packets
	 */
	private static String getFRAG2 (){
		//Note: FC.min_credits must to be > than FRAG2.frag_size
		return "FRAG2(frag_size=50000;" + "down_thread=false;up_thread=false)";
	}
	private static String getJOLSR_UDP(int port){
		return "JOLSR_UDP(" +
		"bind_port="+port+";"+
		"tos=8;" +
		"port_range=1000;" +
		"ucast_recv_buf_size=64000000;" +
		"ucast_send_buf_size=64000000;" +
		"loopback=false;" +
		"discard_incompatible_packets=true;" +
		"max_bundle_size=64000;" +
		"max_bundle_timeout=30;" +
		"use_incoming_packet_handler=true;" +
		"ip_ttl=32;" +
		"enable_bundling=true;" +
		"enable_diagnostics=false;" +
		"thread_naming_pattern=cl;" +		
		"use_concurrent_stack=true;" +		
		"thread_pool.enabled=true;" +
		"thread_pool.min_threads=2;" +
		"thread_pool.max_threads=8;" +
		"thread_pool.keep_alive_time=5000;" +
		"thread_pool.queue_enabled=true;" +
		"thread_pool.queue_max_size=1000;" +
		"thread_pool.rejection_policy=discard;" +
        "oob_thread_pool.enabled=true;" +
        "oob_thread_pool.min_threads=1;" +
        "oob_thread_pool.max_threads=8;" +
        "oob_thread_pool.keep_alive_time=5000;" +
        "oob_thread_pool.queue_enabled=false;" +
        "oob_thread_pool.queue_max_size=100;" +
        "oob_thread_pool.rejection_policy=Run" +
        ")";
	}
	private static String getMulticastProtocol(InetAddress group){
		String multicastProtocol = PropertiesLoader.getMulticastProtocol();
		if (multicastProtocol.equalsIgnoreCase(SMCAST_PROTOCOL)){
			return getSMCAST(group);
		}
		else if (multicastProtocol.equalsIgnoreCase(OMOLSR_PROTOCOL)){
			return getOMOLSR(group);
		}
		return "";
	}
	
	private static String getOLSR(InetAddress group){
		if (group==null){
			// Using OLSR without upper multicast protocol
			return "OLSR";
		} else {
			return "OLSR(mcast_addr="+group.getHostAddress()+")";
		}
	}
	private static String getOMOLSR(InetAddress group){
		return "OMOLSR(mcast_addr="+group.getHostAddress()+")";  /*;bc_port=5555*/ // ;bcast_min_neigh=3
		// TODO Maybe we could pass a multicast port number (that obviously will not be used) only for transparency purposes
	}
	/**
	 * This method returns a configured protocol to perform the message reliability
	 * @param nodeNumber
	 * @return reliable transmission protocol
	 */
	private static String getReliability(int nodeNumber){
		return "JOLSR_UNICAST(timeout=1200,1800,2400,5000,8000;use_gms=false)";
	}
	private static String getSMCAST(InetAddress group){
		return "SMCAST(mcast_addr="+group.getHostAddress()+")"; 
		//TODO Maybe we could pass a multicast port number (that obviously will not be used) only for transparency purposes
	}
}