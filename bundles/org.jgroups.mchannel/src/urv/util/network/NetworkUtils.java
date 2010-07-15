package urv.util.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jgroups.Address;
import org.jgroups.stack.IpAddress;

import urv.conf.PropertiesLoader;
/**
 * Class that provide some methods to manage the different kinds
 * of addresses between JGroups and InetAddress
 * 
 * @author Raul Gracia
 *
 */
public class NetworkUtils {
	
	//	CLASS FIELDS --
	
	private static int defaultPort = PropertiesLoader.getUnicastPort();
	
	//	PUBLIC METHODS --
	
	/**
	 * This method retrieves the JGropups Address equivalent to the
	 * InetAddress given. By default, use the default configuration port,
	 * but before sending the message the port will be tested whether is correct
	 * to the referring node or not.
	 * 
	 * @param dest
	 * @return JGroups Address
	 */
	public static Address getJGroupsAddresFor(InetAddress dest) {
		return new IpAddress(dest, defaultPort);
	}
	/**
	 * This method retrieves the JGropups Address equivalent to the
	 * String Address given. By default, use the default configuration port,
	 * but before sending the message the port will be tested whether is correct
	 * to the referring node or not.
	 * 
	 * @param dest
	 * @return JGroups Address
	 */
	public static Address getJGroupsAddresFor(String dest) {
		try {
			return new IpAddress(dest, defaultPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}
}