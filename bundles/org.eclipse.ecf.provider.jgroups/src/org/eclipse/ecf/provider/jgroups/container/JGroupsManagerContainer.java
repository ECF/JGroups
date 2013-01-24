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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.core.events.ContainerConnectedEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.security.IConnectHandlerPolicy;
import org.eclipse.ecf.core.sharedobject.ISharedObjectContainerConfig;
import org.eclipse.ecf.core.sharedobject.ISharedObjectContainerGroupManager;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.core.util.Trace;
import org.eclipse.ecf.internal.provider.jgroups.Activator;
import org.eclipse.ecf.internal.provider.jgroups.JGroupsDebugOptions;
import org.eclipse.ecf.internal.provider.jgroups.Messages;
import org.eclipse.ecf.internal.provider.jgroups.connection.AbstractJGroupsConnection;
import org.eclipse.ecf.internal.provider.jgroups.connection.ConnectRequestMessage;
import org.eclipse.ecf.internal.provider.jgroups.connection.DisconnectRequestMessage;
import org.eclipse.ecf.internal.provider.jgroups.connection.JGroupsManagerConnection;
import org.eclipse.ecf.provider.comm.IAsynchConnection;
import org.eclipse.ecf.provider.comm.IConnection;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.comm.SynchEvent;
import org.eclipse.ecf.provider.generic.ContainerMessage;
import org.eclipse.ecf.provider.generic.ServerSOContainer;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.stack.IpAddress;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 *
 */
public class JGroupsManagerContainer extends ServerSOContainer implements
		EventHandler {

	public void handleEvent(Event event) {
		System.out.println("event received from client: " + event.toString());
		if (event.getProperty("command").toString().equalsIgnoreCase("evict")) {

			final JGroupsID sender = (JGroupsID) event.getProperty("ID");

			IContainerManager containerManager = (IContainerManager) getAdapter(IContainerManager.class);
			IContainer container = containerManager.getContainer(getID());

			ISharedObjectContainerGroupManager cgm = (ISharedObjectContainerGroupManager) container
					.getAdapter(ISharedObjectContainerGroupManager.class);

			cgm.ejectGroupMember(sender, "evict");
		}
	}

	private IConnectHandlerPolicy joinPolicy = null;

	private ISynchAsynchConnection serverConnection;

	/**
	 * @param config
	 */
	public JGroupsManagerContainer(ISharedObjectContainerConfig config) {
		super(config);
	}

	public Channel getJChannel() {
		return ((AbstractJGroupsConnection) serverConnection).getJChannel();
	}

	/**
	 * Start this server. Subclasses must override this method to start a JMS
	 * server.
	 * 
	 * @throws ECFException
	 *             if some problem with starting the server (e.g. port already
	 *             taken)
	 */
	public void start() throws ECFException {
		serverConnection = new JGroupsManagerConnection(getReceiver());
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
			return handleConnectRequest((ConnectRequestMessage) req,
					(JGroupsManagerConnection) e.getConnection());
		} else if (req instanceof DisconnectRequestMessage) {
			// disconnect them
			final DisconnectRequestMessage dcm = (DisconnectRequestMessage) req;
			final IAsynchConnection conn = getConnectionForID(dcm.getSenderID());
			if (conn != null && conn instanceof JGroupsManagerConnection.Client) {
				final JGroupsManagerConnection.Client client = (JGroupsManagerConnection.Client) conn;
				client.handleDisconnect();
			}
		}
		return null;
	}

	protected void traceAndLogExceptionCatch(int code, String method,
			Throwable e) {
		Trace.catching(Activator.PLUGIN_ID,
				JGroupsDebugOptions.EXCEPTIONS_CATCHING, this.getClass(),
				method, e);
		Activator.getDefault()
				.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, code,
						method, e));
	}

	protected void handleConnectException(ContainerMessage mess,
			JGroupsManagerConnection serverChannel, Exception e) {
	}

	@Override
	protected Object checkJoin(SocketAddress socketAddress, ID fromID,
			String targetPath, Serializable data) throws Exception {
		if (joinPolicy != null)
			return joinPolicy.checkConnect(socketAddress, fromID, getID(),
					targetPath, data);
		else
			return null;
	}

	protected Serializable handleConnectRequest(ConnectRequestMessage request,
			JGroupsManagerConnection connection) {
		Trace.entering(Activator.PLUGIN_ID,
				JGroupsDebugOptions.METHODS_ENTERING, this.getClass(),
				"handleConnectRequest", new Object[] { //$NON-NLS-1$
				request, connection });
		try {
			final ContainerMessage containerMessage = (ContainerMessage) request
					.getData();
			if (containerMessage == null)
				throw new InvalidObjectException(
						Messages.JGroupsServer_CONNECT_EXCEPTION_CONTAINER_MESSAGE_NOT_NULL);
			final ID remoteID = containerMessage.getFromContainerID();
			if (remoteID == null)
				throw new InvalidObjectException("remoteID cannot be null");
			JGroupsID jgid = null;
			if (remoteID instanceof JGroupsID) {
				jgid = (JGroupsID) remoteID;
			} else
				throw new InvalidObjectException(
						"remoteID not of JGroupsID type");
			final ContainerMessage.JoinGroupMessage jgm = (ContainerMessage.JoinGroupMessage) containerMessage
					.getData();
			if (jgm == null)
				throw new InvalidObjectException(
						Messages.JGroupsServer_CONNECT_EXCEPTION_JOINGROUPMESSAGE_NOT_NULL);
			ID memberIDs[] = null;
			final Serializable[] messages = new Serializable[2];
			JGroupsManagerConnection.Client newclient = null;
			synchronized (getGroupMembershipLock()) {
				if (isClosing)
					throw new ContainerConnectException(
							Messages.JGroupsServer_CONNECT_EXCEPTION_CONTAINER_CLOSING);
				final Address address = jgid.getAddress();
				int port = -1;
				InetAddress host = null;
				if (address instanceof IpAddress) {
					port = ((IpAddress) address).getPort();
					host = ((IpAddress) address).getIpAddress();
				}
				// Now check to see if this request is going to be allowed
				checkJoin(new InetSocketAddress(host, port), jgid, request
						.getTargetID().getChannelName(), jgm.getData());

				newclient = connection.new Client(jgid);

				if (addNewRemoteMember(jgid, newclient)) {
					// Get current membership
					memberIDs = getGroupMemberIDs();
					// Notify existing remotes about new member
					messages[1] = serialize(ContainerMessage
							.createViewChangeMessage(getID(), null,
									getNextSequenceNumber(), new ID[] { jgid },
									true, null));
				} else {
					final ConnectException e = new ConnectException(
							Messages.JGroupsServer_CONNECT_EXCEPTION_REFUSED);
					throw e;
				}
			}
			// notify listeners
			fireContainerEvent(new ContainerConnectedEvent(this.getID(), jgid));

			messages[0] = serialize(ContainerMessage.createViewChangeMessage(
					getID(), jgid, getNextSequenceNumber(), memberIDs, true,
					null));

			newclient.start();

			return messages;

		} catch (final Exception e) {
			traceAndLogExceptionCatch(IStatus.ERROR, "handleConnectRequest", e);
			return null;
		}
	}

	@Override
	protected void forwardExcluding(ID from, ID excluding, ContainerMessage data)
			throws IOException {
		// no forwarding necessary
	}

	@Override
	protected void forwardToRemote(ID from, ID to, ContainerMessage data)
			throws IOException {
		// no forwarding necessary
	}

	@Override
	protected void queueContainerMessage(ContainerMessage mess)
			throws IOException {
		serverConnection.sendAsynch(mess.getToContainerID(), serialize(mess));
	}

	@Override
	protected void handleLeave(ID target, IConnection conn) {
		if (target == null)
			return;
		if (removeRemoteMember(target)) {
			try {
				queueContainerMessage(ContainerMessage.createViewChangeMessage(
						getID(), null, getNextSequenceNumber(),
						new ID[] { target }, false, null));
			} catch (final IOException e) {
				traceAndLogExceptionCatch(IStatus.ERROR, "memberLeave", e); //$NON-NLS-1$
			}
		}
		if (conn != null)
			disconnect(conn);
	}

}
