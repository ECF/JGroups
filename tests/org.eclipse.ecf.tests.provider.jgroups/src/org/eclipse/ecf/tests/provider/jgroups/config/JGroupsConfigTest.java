package org.eclipse.ecf.tests.provider.jgroups.config;

import java.net.URI;
import java.net.URL;

import org.eclipse.ecf.internal.tests.provider.jgroups.Activator;
import org.eclipse.ecf.internal.tests.provider.jgroups.JGroups;
import org.eclipse.ecf.internal.tests.provider.jgroups.JGroupsContainerTest;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.jgroups.JChannelFactory;

public class JGroupsConfigTest extends JGroupsContainerTest {


	private final static String stackConfigID = "org.eclipse.ecf.provider.jgroups.default";

	/**
	 * stackNamesAvailable lists all 'stacks' contained in file referenced by
	 * stackConfigID (see extension in o.e.e.jgroups plugin)
	 * 'bas_config' is NOT present so an error is detected.
	 */
	private final static String[] stackNamesAvailable = { "udp", "udp-sync",
			"tcp", "tcp-sync", "tcp-nio", "tcp-nio-sync", "tunnel",
			"encrypt_entire_message", "encrypt", "bad_config" };


	private String getServerIdentity(int index) {
		String uri = JGroups.URI_CONFIG_NAME;
		return uri + "?stackName=" + stackNamesAvailable[index]
				+ "&stackConfigID=" + stackConfigID;
	}

	private boolean testUri(int index) {
		URI uri = null;
		try {
			uri = new URI(getServerIdentity(index));
		} catch (Exception e) {
			assertFalse("bad jgroups uri: " + e.getMessage(), true);
		}
		JGroupsID targetID = new JGroupsID(new JGroupsNamespace(), uri);
		final JChannelFactory factory = new JChannelFactory();
		final String stackConfigID = targetID.getStackConfigID();
		URL stackConfigURL = null;
		stackConfigURL = Activator.getDefault().getConfigURLForStackID(
				stackConfigID);

		try {
			factory.setMultiplexerConfig(stackConfigURL);
			factory.createMultiplexerChannel(targetID.getStackName(),
					targetID.getName());

		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public void testAllGoodStacks() {
		for (int i = 0; i < stackNamesAvailable.length-1; i++) {
			assertTrue(testUri(i));
		}
	}
	
	public void testBadStacks(){
		assertFalse(testUri(stackNamesAvailable.length-1));
	}

	/**
	 * overrided to not run this test which is run in  {@link JGroupsContainerTest}
	 */
	@Override
	public void testConnectClient() throws Exception {
	}

}
