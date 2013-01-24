/****************************************************************************
 * Copyright (c) 2004 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/

package org.eclipse.ecf.tests.provider.jgroups.remoteservice;

import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.remoteservice.IRemoteCall;
import org.eclipse.ecf.remoteservice.IRemoteService;
import org.eclipse.ecf.remoteservice.IRemoteServiceContainerAdapter;
import org.eclipse.ecf.remoteservice.IRemoteServiceListener;
import org.eclipse.ecf.remoteservice.IRemoteServiceReference;
import org.eclipse.ecf.remoteservice.IRemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.events.IRemoteServiceEvent;
import org.eclipse.ecf.tests.ContainerAbstractTestCase;
import org.osgi.framework.InvalidSyntaxException;

/**
 * 
 */
public abstract class AbstractRemoteServiceTestCase extends
		ContainerAbstractTestCase {

	protected IRemoteServiceContainerAdapter[] adapters = null;

	protected abstract String getServerContainerName();

	protected abstract String getClientContainerName();

	protected abstract String getServerIdentity();

	protected String getJGroupsNamespace() {
		return "ecf.namespace.jgroupsid";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		setClientCount(2);
		createServerAndClients();
		connectClients();
		setupRemoteServiceAdapters();
		addRemoteServiceListeners();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		cleanUpServerAndClients();
		super.tearDown();
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

	protected void setClientCount(int count) {
		super.setClientCount(count);
		adapters = new IRemoteServiceContainerAdapter[count];
	}

	protected abstract Object createService();

	protected void setupRemoteServiceAdapters() throws Exception {
		final int clientCount = getClientCount();
		for (int i = 0; i < clientCount; i++) {
			adapters[i] = (IRemoteServiceContainerAdapter) getClients()[i]
					.getAdapter(IRemoteServiceContainerAdapter.class);
		}
	}

	protected IRemoteServiceContainerAdapter[] getRemoteServiceAdapters() {
		return adapters;
	}

	protected IRemoteServiceListener createRemoteServiceListener() {
		return new IRemoteServiceListener() {
			public void handleServiceEvent(IRemoteServiceEvent event) {
				System.out.println("handleServiceEvent(" + event + ")");
			}
		};
	}

	protected void addRemoteServiceListeners() {
		for (int i = 0; i < adapters.length; i++) {
			adapters[i].addRemoteServiceListener(createRemoteServiceListener());
		}
	}

	protected IRemoteServiceRegistration registerService(
			IRemoteServiceContainerAdapter adapter, String serviceInterface,
			Object service, int sleepTime) {
		final IRemoteServiceRegistration result = adapter
				.registerRemoteService(new String[] { serviceInterface },
						service, null);
		sleep(sleepTime);
		return result;
	}

	protected IRemoteServiceReference[] getRemoteServiceReferences(
			IRemoteServiceContainerAdapter adapter, String clazz) {
		try {
			return adapter.getRemoteServiceReferences((ID[]) null, clazz, null);
		} catch (final InvalidSyntaxException e) {
			fail("should not happen");
		}
		return null;
	}

	protected IRemoteService getRemoteService(
			IRemoteServiceContainerAdapter adapter, String clazz) {
		final IRemoteServiceReference[] refs = getRemoteServiceReferences(
				adapter, clazz);
		if (refs.length == 0)
			return null;
		return adapter.getRemoteService(refs[0]);
	}

	protected IRemoteService registerAndGetRemoteService(
			IRemoteServiceContainerAdapter server,
			IRemoteServiceContainerAdapter client, String serviceName,
			int sleepTime) {
		registerService(server, serviceName, createService(), sleepTime);
		return getRemoteService(client, serviceName);
	}

	protected IRemoteCall createRemoteCall(final String method,
			final Object[] params) {
		return new IRemoteCall() {
			public String getMethod() {
				return method;
			}

			public Object[] getParameters() {
				return params;
			}

			public long getTimeout() {
				return 3000;
			}
		};
	}
}
