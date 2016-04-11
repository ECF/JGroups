/*******************************************************************************
 * Copyright (c) 2016 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.provider.comm.ConnectionCreateException;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.generic.ClientSOContainer;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.jgroups.JChannel;

public class JGroupsClientContainer extends ClientSOContainer {

	public static final String JGROUPS_CLIENT_CONFIG = "ecf.jgroups.client";

	public static final String JGROUPS_CLIENTID_PROP = "clientId";
	public static final String JGROUPS_CLIENT_CHANNEL_CONFIG_URL = "clientChannelConfigUrl";
	public static final String JGROUPS_CLIENT_CHANNEL_CONFIG_STRING = "clientChannelConfigString";
	public static final String JGROUPS_CLIENT_CHANNEL_CONFIG_INPUTSTREAM = "clientChannelConfigInputStream";

	private final JChannel channel;

	public JGroupsClientContainer(SOContainerConfig config) throws IDCreateException {
		this(config, null);
	}

	public JGroupsClientContainer(SOContainerConfig config, JChannel channel) {
		super(config);
		this.channel = channel;
	}

	@Override
	public Namespace getConnectNamespace() {
		return JGroupsNamespace.INSTANCE;
	}

	@Override
	protected ISynchAsynchConnection createConnection(ID remoteSpace, Object data) throws ConnectionCreateException {
		return new JGroupsClientConnection(getReceiver(), channel);
	}

}
