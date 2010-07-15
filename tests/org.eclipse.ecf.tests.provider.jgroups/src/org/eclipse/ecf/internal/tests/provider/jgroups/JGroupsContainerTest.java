package org.eclipse.ecf.internal.tests.provider.jgroups;

import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.tests.ContainerAbstractTestCase;

public class JGroupsContainerTest extends ContainerAbstractTestCase {

	protected String getClientContainerName() {
		return JGroups.CLIENT_CONTAINER_NAME;
	}

	protected String getServerIdentity() {
		return JGroups.TARGET_NAME;
	}

	protected String getServerContainerName() {
		return JGroups.SERVER_CONTAINER_NAME;
	}

	protected String getJGroupsNamespace() {
		return "ecf.namespace.jgroupsid";
	}

	protected IContainer createServer() throws Exception {
		return ContainerFactory.getDefault().createContainer(
				getServerContainerName(), new Object[] { getServerIdentity() });
	}

	protected void setUp() throws Exception {
		setClientCount(2);
		createServerAndClients();
		super.setUp();
	}

	protected void tearDown() throws Exception {
		cleanUpServerAndClients();
		super.tearDown();
	}

	public void testConnectClient() throws Exception {
		final IContainer client = getClients()[0];
		final ID targetID = IDFactory.getDefault().createID(
				client.getConnectNamespace(),
				new Object[] { getServerIdentity() });
		client.connect(targetID, null);
		Thread.sleep(3000);
		final IContainer client1 = getClients()[1];
		client1.connect(targetID, null);
		Thread.sleep(6000);
	}
}
