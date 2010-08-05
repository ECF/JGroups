/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups.connection;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.core.util.Trace;
import org.eclipse.ecf.internal.provider.jgroups.Activator;
import org.eclipse.ecf.internal.provider.jgroups.JGroupsDebugOptions;
import org.eclipse.ecf.provider.comm.DisconnectEvent;
import org.eclipse.ecf.provider.comm.IConnectionListener;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.comm.ISynchAsynchEventHandler;
import org.eclipse.ecf.provider.comm.SynchEvent;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.osgi.util.NLS;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.MessageDispatcher;

/**
 *
 */
@SuppressWarnings("unchecked")
public class JGroupsManagerConnection extends AbstractJGroupsConnection {

	private static final long DEFAULT_DISCONNECT_TIMEOUT = 3000;

	/**
	 * @param eventHandler
	 * @throws ECFException
	 */
	public JGroupsManagerConnection(ISynchAsynchEventHandler eventHandler)
			throws ECFException {
		super(eventHandler);
		setupJGroups((JGroupsID) getLocalID());
	}

	@Override
	public Object connect(ID targetID, Object data, int timeout)
			throws ECFException {
		throw new ECFException("Server cannot connect");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.provider.jgroups.connection.AbstractJGroupsConnection
	 * #internalHandleSynch(org.jgroups.Message)
	 */
	@Override
	protected Object internalHandleSynch(Message message) {
		final Object o = message.getObject();
		if (o == null) {
			logMessageError("object in message is null", message);
			return null;
		}
		try {
			return eventHandler.handleSynchEvent(new SynchEvent(this, o));
		} catch (final IOException e) {
			Trace.catching(Activator.PLUGIN_ID,
					JGroupsDebugOptions.EXCEPTIONS_CATCHING, this.getClass(),
					"internalHandleSynch", e);
			return null;
		}
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.provider.jgroups.connection.AbstractJGroupsConnection
	 * #sendSynch(org.eclipse.ecf.core.identity.ID, byte[])
	 */
	@Override
	public Object sendSynch(ID receiver, byte[] data) throws IOException {
		final MessageDispatcher messageDispatcher = getMessageDispatcher();
		if (receiver == null || !(receiver instanceof JGroupsID))
			throw new IOException("invalid receiver id");
		final Message msg = new Message(null, null,
				new DisconnectRequestMessage((JGroupsID) getLocalID(),
						(JGroupsID) receiver, data));
		Object response = null;
		try {
			response = messageDispatcher.sendMessage(msg,
					GroupRequest.GET_FIRST, DEFAULT_DISCONNECT_TIMEOUT);
		} catch (final Exception e) {
			Trace.catching(Activator.PLUGIN_ID,
					JGroupsDebugOptions.EXCEPTIONS_CATCHING, this.getClass(),
					"sendSynch", e);
			throw new IOException("disconnect timeout");
		}
		return response;
	}

	public Client createClient(JGroupsID remoteID) {
		Client newclient = new Client(remoteID);
		newclient.start();
		return newclient;
	}

	public class Client implements ISynchAsynchConnection {

		private final JGroupsID clientID;
		private boolean isConnected = true;
		private boolean isStarted = false;
		private final Object disconnectLock = new Object();
		private boolean disconnectHandled = false;

		public Client(JGroupsID clientID) {
			this.clientID = clientID;
			final Address addr = this.clientID.getAddress();
			if (addr != null) {
				addClientToMap(addr, this);
			}
		}

		public void sendAsynch(ID receiver, byte[] data) throws IOException {
			JGroupsManagerConnection.this.sendAsynch(receiver, data);
		}

		public void addListener(IConnectionListener listener) {
		}

		public Object connect(ID targetID, Object data, int timeout)
				throws ECFException {
			throw new ECFException("Server cannot connect");
		}

		public void disconnect() {
			isConnected = false;
			stop();
			removeClientFromMap(clientID.getAddress());
		}

		public ID getLocalID() {
			return clientID;
		}

		public Map getProperties() {
			return null;
		}

		public boolean isConnected() {
			return isConnected;
		}

		public boolean isStarted() {
			return isStarted;
		}

		public void removeListener(IConnectionListener listener) {
		}

		public void start() {
			isStarted = true;
		}

		public void stop() {
			isStarted = false;
		}

		public Object getAdapter(Class adapter) {
			return null;
		}

		public Object sendSynch(ID receiver, byte[] data) throws IOException {
			return JGroupsManagerConnection.this.sendSynch(receiver, data);
		}

		public DisconnectResponseMessage handleDisconnect(JGroupsID senderID) {
			DisconnectResponseMessage result = null;
			synchronized (disconnectLock) {
				if (!disconnectHandled) {
					disconnectHandled = true;
					eventHandler.handleDisconnectEvent(new DisconnectEvent(
							Client.this, null, null));
					result = new DisconnectResponseMessage((JGroupsID) getLocalID(),
							senderID, null);
				}
			}
			synchronized (Client.this) {
				Client.this.notifyAll();
			}
			return result;
		}
		
		public ConnectResponseMessage createConnectResponseMessage(JGroupsID senderID, Serializable[] resp) throws IOException {
			// send second resp array value
			sendAsynch(null, (byte[]) resp[1]);
			// return new Connect response message with first array value
			return new ConnectResponseMessage(
					(JGroupsID) getLocalID(), senderID, resp[0]);
		}
	}

	private View oldView = null;

	private List memberDiff(List oldMembers, List newMembers) {
		final List result = new ArrayList();
		for (final Iterator i = oldMembers.iterator(); i.hasNext();) {
			final Address addr1 = (Address) i.next();
			if (!newMembers.contains(addr1))
				result.add(addr1);
		}
		return result;
	}

	@Override
	protected void handleViewAccepted(View view) {
		Trace.trace(Activator.PLUGIN_ID, "viewAccepted(" + view + ")");
		if (oldView == null) {
			oldView = view;
			return;
		} else {
			final List departed = memberDiff(oldView.getMembers(), view
					.getMembers());
			if (departed.size() > 0) {
				Trace
						.trace(Activator.PLUGIN_ID, "members departed="
								+ departed);
				for (final Iterator i = departed.iterator(); i.hasNext();) {
					final Address addr = (Address) i.next();
					final Client client = getClientForAddress(addr);
					if (client != null) handleDisconnectInThread(client);
				}
			}
			oldView = view;
		}
	}

	private void handleDisconnectInThread(final Client client) {
		final Thread t = new Thread(new Runnable() {
			public void run() {
				eventHandler.handleDisconnectEvent(new DisconnectEvent(client,
						new Exception(NLS.bind("member %1 disconnected",
								client.clientID)), null));
			}
		});
		t.start();
	}

	private final Map addressClientMap = Collections
			.synchronizedMap(new HashMap());

	private void addClientToMap(Address addr, Client client) {
		addressClientMap.put(addr, client);
	}

	private void removeClientFromMap(Address addr) {
		addressClientMap.remove(addr);
	}

	/**
	 * @param addr
	 * @return
	 */
	private Client getClientForAddress(Address addr) {
		if (addr == null)
			return null;
		return (Client) addressClientMap.get(addr);
	}

}
