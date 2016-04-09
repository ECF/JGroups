/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import java.io.NotSerializableException;
import java.io.Serializable;

import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.comm.ConnectionEvent;
import org.eclipse.ecf.provider.comm.ISynchAsynchEventHandler;
import org.eclipse.ecf.provider.generic.ContainerMessage;
import org.eclipse.ecf.provider.generic.SOContainer;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.jgroups.JChannel;
import org.jgroups.View;

public class JGroupsClientConnection extends AbstractJGroupsConnection {

	public JGroupsClientConnection(ISynchAsynchEventHandler eventHandler, JChannel channel) {
		super(eventHandler, channel);
	}

	@Override
	public synchronized Object connect(ID targetID, Object data, int timeout) throws ECFException {
		if (isConnected())
			throw new ContainerConnectException("Already connected");//$NON-NLS-1$
		if (targetID == null)
			throw new ContainerConnectException("TargetID must not be null");//$NON-NLS-1$
		if (!(targetID instanceof JGroupsID))
			throw new ContainerConnectException("Target ID not of JGroupsID namespace");
		if (!(data instanceof Serializable)) {
			throw new ContainerConnectException("Connect Failed",
					new NotSerializableException("Data not serializable"));
		}
		ConnectResponseMessage response = null;
		try {
			final JGroupsID jgroupsID = (JGroupsID) targetID;
			setupJGroups(jgroupsID);
			response = (ConnectResponseMessage) sendMessageAndWait(
					new ConnectRequestMessage(getLocalID(), jgroupsID, serializeToBytes(data)), timeout);
		} catch (final Exception e) {
			ContainerConnectException cce = new ContainerConnectException(
					"Connect to targetID=" + targetID.getName() + " failed", e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
		Object connectResponseResult = null;
		try {
			connectResponseResult = SOContainer.deserializeContainerMessage((byte[]) response.getData());
		} catch (final Exception e) {
			throw new ContainerConnectException("Could not deserialize connect response", e);
		}
		if (connectResponseResult == null || !(connectResponseResult instanceof ContainerMessage))
			throw new ContainerConnectException("Server response not of type ContainerMessage");
		fireListenersConnect(new ConnectionEvent(this, connectResponseResult));
		return connectResponseResult;
	}

	@Override
	protected void handleSyncMessage(SyncMessage message) {
		setSyncResponse(message);
	}

	@Override
	protected void handleViewAccepted(View view) {
	}
}
