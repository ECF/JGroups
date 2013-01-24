/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import java.util.Hashtable;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.internal.provider.jgroups.Activator;
import org.eclipse.ecf.internal.provider.jgroups.connection.AbstractJGroupsConnection;
import org.eclipse.ecf.internal.provider.jgroups.connection.JGroupsClientConnection;
import org.eclipse.ecf.provider.comm.ConnectionCreateException;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.generic.ClientSOContainer;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.remoteservice.eventadmin.DistributedEventAdmin;
import org.jgroups.Channel;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;

/**
 * Trivial container implementation. that container adapter implementations can
 * be provided by the container class to expose appropriate adapters.
 */
public class JGroupsClientContainer extends ClientSOContainer implements
		EventAdmin {

	private final DistributedEventAdmin eventAdminImpl;

	public JGroupsClientContainer(SOContainerConfig config)
			throws IDCreateException {
		super(config);
		// hook in context for events
		final BundleContext context = Activator.getDefault().getContext();
		eventAdminImpl = new DistributedEventAdmin(context);
		eventAdminImpl.start();

		// register as EventAdmin service instance
		Hashtable<String, Object> props0 = new Hashtable<String, Object>();
		props0.put(EventConstants.EVENT_TOPIC, "*");
		context.registerService("org.osgi.service.event.EventAdmin",
				eventAdminImpl, props0);

	}

	@Override
	public Namespace getConnectNamespace() {
		return IDFactory.getDefault().getNamespaceByName(JGroupsNamespace.NAME);
	}

	@Override
	protected ISynchAsynchConnection createConnection(ID remoteSpace,
			Object data) throws ConnectionCreateException {
		return new JGroupsClientConnection(getReceiver());
	}

	public Channel getJChannel() {
		synchronized (getConnectLock()) {
			if (isConnected())
				return ((AbstractJGroupsConnection) getConnection())
						.getJChannel();
			return null;
		}
	}

	public void postEvent(Event event) {
		this.eventAdminImpl.postEvent(event);
	}

	public void sendEvent(Event event) {
		this.eventAdminImpl.sendEvent(event);
	}

}
