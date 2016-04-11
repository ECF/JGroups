/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.SocketAddress;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.events.ContainerConnectedEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.security.IConnectHandlerPolicy;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.core.util.Trace;
import org.eclipse.ecf.internal.provider.jgroups.Activator;
import org.eclipse.ecf.internal.provider.jgroups.JGroupsDebugOptions;
import org.eclipse.ecf.provider.comm.IAsynchConnection;
import org.eclipse.ecf.provider.comm.IConnection;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.comm.SynchEvent;
import org.eclipse.ecf.provider.generic.ContainerMessage;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.generic.ServerSOContainer;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.remoteservice.util.ObjectSerializationUtil;
import org.jgroups.JChannel;

public class JGroupsManagerContainer extends ServerSOContainer {

	public static final String JGROUPS_MANAGER_CONFIG = "ecf.jgroups.manager";
	public static final String JGROUPS_MANAGERID_PROP = "managerId";
	public static final String JGROUPS_MANAGER_ID_DEFAULT = JGroupsNamespace.INSTANCE.getScheme()
			+ ":ecf.jgroups.defaultGroup";
	public static final String JGROUPS_MANAGER_CHANNEL_CONFIG_URL = "managerChannelConfigUrl";
	public static final String JGROUPS_MANAGER_CHANNEL_CONFIG_STRING = "managerChannelConfigString";
	public static final String JGROUPS_MANAGER_CHANNEL_CONFIG_INPUTSTREAM = "managerChannelConfigInputStream";

	private IConnectHandlerPolicy joinPolicy = null;
	private ISynchAsynchConnection serverConnection;
	private JChannel channel;

	public JGroupsManagerContainer(JGroupsID id) {
		this(id, null);
	}

	public JGroupsManagerContainer(JGroupsID id, JChannel channel) {
		super(new SOContainerConfig(id));
		this.channel = channel;
	}

	public JGroupsManagerContainer(SOContainerConfig config, JChannel channel) {
		super(config);
		this.channel = channel;
	}

	public JGroupsManagerContainer(SOContainerConfig config) {
		this(config, null);
	}

	public void start() throws ECFException {
		serverConnection = new JGroupsManagerConnection(getReceiver(), channel);
		serverConnection.start();
	}

	@Override
	public void dispose() {
		getConnection().disconnect();
		setConnection(null);
		super.dispose();
	}

	protected void setConnection(ISynchAsynchConnection channel) {
		this.serverConnection = channel;
	}

	protected ISynchAsynchConnection getConnection() {
		return serverConnection;
	}

	protected IConnectHandlerPolicy getConnectHandlerPolicy() {
		return joinPolicy;
	}

	protected void setConnectHandlerPolicy(IConnectHandlerPolicy policy) {
		this.joinPolicy = policy;
	}

	@Override
	protected Serializable processSynch(SynchEvent e) throws IOException {
		final Object req = e.getData();
		if (req instanceof ConnectRequestMessage) {
			return handleConnectRequest((ConnectRequestMessage) req, (JGroupsManagerConnection) e.getConnection());
		} else if (req instanceof DisconnectRequestMessage) {
			// disconnect them
			final DisconnectRequestMessage dcm = (DisconnectRequestMessage) req;
			final IAsynchConnection conn = getConnectionForID(dcm.getFromID());
			if (conn != null && conn instanceof JGroupsManagerConnection.Client) {
				final JGroupsManagerConnection.Client client = (JGroupsManagerConnection.Client) conn;
				client.handleDisconnect();
			}
		}
		return null;
	}

	protected void traceAndLogExceptionCatch(int code, String method, Throwable e) {
		Trace.catching(Activator.PLUGIN_ID, JGroupsDebugOptions.EXCEPTIONS_CATCHING, this.getClass(), method, e);
		Activator.getDefault().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, code, method, e));
	}

	protected void handleConnectException(ContainerMessage mess, JGroupsManagerConnection serverChannel, Exception e) {
	}

	@Override
	protected Object checkJoin(SocketAddress socketAddress, ID fromID, String targetPath, Serializable data)
			throws Exception {
		if (joinPolicy != null)
			return joinPolicy.checkConnect(socketAddress, fromID, getID(), targetPath, data);
		else
			return null;
	}

	ObjectSerializationUtil osu = new ObjectSerializationUtil();

	protected Serializable handleConnectRequest(ConnectRequestMessage request, JGroupsManagerConnection connection) {
		try {
			final ContainerMessage containerMessage = (ContainerMessage) osu.deserializeFromBytes(request.getData());
			if (containerMessage == null)
				throw new InvalidObjectException("Invalid container message");
			final ID remoteID = containerMessage.getFromContainerID();
			if (remoteID == null)
				throw new InvalidObjectException("remoteID cannot be null");
			JGroupsID jgid = null;
			if (remoteID instanceof JGroupsID) {
				jgid = (JGroupsID) remoteID;
			} else
				throw new InvalidObjectException("remoteID not of JGroupsID type");
			final ContainerMessage.JoinGroupMessage jgm = (ContainerMessage.JoinGroupMessage) containerMessage
					.getData();
			if (jgm == null)
				throw new InvalidObjectException("Join group message cannot be null");
			ID memberIDs[] = null;
			final Serializable[] messages = new Serializable[2];
			JGroupsManagerConnection.Client newclient = null;
			synchronized (getGroupMembershipLock()) {
				if (isClosing)
					throw new ContainerConnectException("Container is closing");
				// Now check to see if this request is going to be allowed
				checkJoin(null, jgid, request.getTargetID().getChannelName(), jgm.getData());

				newclient = connection.new Client(jgid);

				if (addNewRemoteMember(jgid, newclient)) {
					// Get current membership
					memberIDs = getGroupMemberIDs();
					// Notify existing remotes about new member
					messages[1] = serialize(ContainerMessage.createViewChangeMessage(getID(), null,
							getNextSequenceNumber(), new ID[] { jgid }, true, null));
				} else {
					final ConnectException e = new ConnectException("Connection refused");
					throw e;
				}
			}
			// notify listeners
			fireContainerEvent(new ContainerConnectedEvent(this.getID(), jgid));

			messages[0] = serialize(ContainerMessage.createViewChangeMessage(getID(), jgid, getNextSequenceNumber(),
					memberIDs, true, null));

			newclient.start();

			return messages;

		} catch (final Exception e) {
			traceAndLogExceptionCatch(IStatus.ERROR, "handleConnectRequest", e);
			return null;
		}
	}

	@Override
	protected void forwardExcluding(ID from, ID excluding, ContainerMessage data) throws IOException {
		// no forwarding necessary
	}

	@Override
	protected void forwardToRemote(ID from, ID to, ContainerMessage data) throws IOException {
		// no forwarding necessary
	}

	@Override
	protected void queueContainerMessage(ContainerMessage mess) throws IOException {
		serverConnection.sendAsynch(mess.getToContainerID(), serialize(mess));
	}

	@Override
	protected void handleLeave(ID target, IConnection conn) {
		if (target == null)
			return;
		if (removeRemoteMember(target)) {
			try {
				queueContainerMessage(ContainerMessage.createViewChangeMessage(getID(), null, getNextSequenceNumber(),
						new ID[] { target }, false, null));
			} catch (final IOException e) {
				traceAndLogExceptionCatch(IStatus.ERROR, "memberLeave", e); //$NON-NLS-1$
			}
		}
		if (conn != null)
			disconnect(conn);
	}

}
