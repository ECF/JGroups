package org.eclipse.ecf.internal.tests.provider.jgroups;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IReliableContainer;
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

	void assertContainsMembers(IReliableContainer container, ID...idsToTest) {
		List<ID> memberIDs = Arrays.asList(container.getGroupMemberIDs());
		for(ID id: idsToTest) 
			assertTrue(memberIDs.contains(id));
	}
	
	public void testConnectAndDisconnectClients () throws Exception {
		final IReliableContainer client = getClients()[0].getAdapter(IReliableContainer.class);
		assertNotNull(client);
		final IReliableContainer client1 = getClients()[1].getAdapter(IReliableContainer.class);
		assertNotNull(client);
		final IReliableContainer server = getServer().getAdapter(IReliableContainer.class);
		assertNotNull(server);
		final ID targetID = IDFactory.getDefault().createID(
				client.getConnectNamespace(),
				new Object[] { getServerIdentity() });
		// connect first client
		client.connect(targetID, null);
		//test member state
		assertEquals(targetID,client.getConnectedID());
		assertEquals(Arrays.asList(server.getGroupMemberIDs()).size(), 2);
		assertEquals(Arrays.asList(client.getGroupMemberIDs()).size(), 2);
		assertContainsMembers(server, client.getID(), targetID);
		assertContainsMembers(client, client.getID(), targetID);
		// connect second client
		client1.connect(targetID, null);
		Thread.sleep(3000);
		assertEquals(targetID,client1.getConnectedID());
		assertEquals(Arrays.asList(server.getGroupMemberIDs()).size(), 3);
		assertEquals(Arrays.asList(client1.getGroupMemberIDs()).size(), 3);
		assertEquals(Arrays.asList(client.getGroupMemberIDs()).size(), 3);
		assertContainsMembers(server, client.getID(), client1.getID(), targetID);
		assertContainsMembers(client, client.getID(), client1.getID(), targetID);
		assertContainsMembers(client1, client.getID(), client1.getID(), targetID);
		// Now disconnect
		client1.disconnect();
		Thread.sleep(3000);
		assertEquals(client1.getConnectedID(),null);
		assertEquals(Arrays.asList(server.getGroupMemberIDs()).size(), 2);
		assertEquals(Arrays.asList(client.getGroupMemberIDs()).size(), 2);
		assertEquals(Arrays.asList(client1.getGroupMemberIDs()).size(), 1);
		client.disconnect();
		Thread.sleep(3000);
		assertEquals(client.getConnectedID(),null);
		assertEquals(Arrays.asList(server.getGroupMemberIDs()).size(), 1);
		assertEquals(Arrays.asList(client.getGroupMemberIDs()).size(), 1);
		assertEquals(Arrays.asList(client1.getGroupMemberIDs()).size(), 1);
	}
}
