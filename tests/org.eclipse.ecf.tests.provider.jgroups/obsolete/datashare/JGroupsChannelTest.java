/****************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/

package org.eclipse.ecf.tests.provider.jgroups.datashare;

import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.datashare.IChannelListener;
import org.eclipse.ecf.datashare.events.IChannelEvent;
import org.eclipse.ecf.datashare.events.IChannelMessageEvent;
import org.eclipse.ecf.internal.tests.provider.jgroups.JGroups;
import org.eclipse.ecf.tests.datashare.ChannelTest;

/**
 *
 */
public class JGroupsChannelTest extends ChannelTest {

	protected String getServerContainerName() {
		return JGroups.SERVER_CONTAINER_NAME;
	}

	protected String getClientContainerName() {
		return JGroups.CLIENT_CONTAINER_NAME;
	}

	protected String getServerIdentity() {
		return JGroups.TARGET_NAME;
	}

	protected String getJGroupsNamespace() {
		return "ecf.namespace.jgroupsid";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		setClientCount(4);
		createServerAndClients();
		addChannelToClients();
		connectClients();
	}

	protected ID createServerID() throws Exception {
		return IDFactory.getDefault().createID(
				IDFactory.getDefault()
						.getNamespaceByName(getJGroupsNamespace()),
				new Object[] { getServerIdentity() });
	}

	protected IContainer createServer() throws Exception {
		return ContainerFactory.getDefault().createContainer(
				getServerContainerName(), new Object[] { getServerIdentity() });
	}

	/**
	 * @return
	 */
	protected IChannelListener getIChannelListener(final ID id)
			throws Exception {
		return new IChannelListener() {
			public void handleChannelEvent(IChannelEvent event) {
				if (event instanceof IChannelMessageEvent) {
					// IChannelMessageEvent cme = (IChannelMessageEvent) event;
					messageEvents.put(id, event);
					System.out.println(id + ".handleChannelEvent(" + event
							+ ")");
				}
			}
		};
	}

}
