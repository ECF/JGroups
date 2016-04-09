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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.comm.DisconnectEvent;
import org.eclipse.ecf.provider.comm.IConnectionListener;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.comm.ISynchAsynchEventHandler;
import org.eclipse.ecf.provider.comm.SynchEvent;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.View;

/**
 *
 */
@SuppressWarnings("unchecked")
public class JGroupsManagerConnection extends AbstractJGroupsConnection {

	public JGroupsManagerConnection(ISynchAsynchEventHandler eventHandler, JChannel channel) throws ECFException {
		super(eventHandler, channel);
		setupJGroups(getLocalID());
	}

	@Override
	public Object connect(ID targetID, Object data, int timeout) throws ECFException {
		throw new ECFException("Server cannot connect");
	}

	@Override
	protected void handleSyncMessage(SyncMessage message) {
		try {
			final Serializable[] resp = (Serializable[]) getEventHandler()
					.handleSynchEvent(new SynchEvent(this, message));
			// this resp is an Serializable[] with two messages, one for the
			// connect response and the other for everyone else
			if (message instanceof ConnectRequestMessage) {
				sendMessage(new ConnectResponseMessage(getLocalID(), message.getFromID(), (byte[]) resp[0]));
			}
		} catch (final Exception e) {
			logException("handleSyncMessage:exception", e);
		}
	}

	public class Client implements ISynchAsynchConnection {

		private final JGroupsID clientID;
		private boolean isConnected = true;
		private boolean isStarted = false;
		private final Object disconnectLock = new Object();
		private boolean disconnectHandled = false;

		public Client(JGroupsID clientID) {
			this.clientID = clientID;
			addClientToMap(clientID.getAddress(), this);
		}

		public void sendAsynch(ID receiver, byte[] data) throws IOException {
			JGroupsManagerConnection.this.sendAsynch(receiver, data);
		}

		public void addListener(IConnectionListener listener) {
		}

		public Object connect(ID targetID, Object data, int timeout) throws ECFException {
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

		public void handleDisconnect() {
			synchronized (disconnectLock) {
				if (!disconnectHandled) {
					disconnectHandled = true;
					JGroupsManagerConnection.this.getEventHandler()
							.handleDisconnectEvent(new DisconnectEvent(Client.this, null, null));
				}
			}
			synchronized (Client.this) {
				Client.this.notifyAll();
			}
		}
	}

	private View oldView = null;

	private List<Address> memberDiff(List<Address> oldMembers, List<Address> newMembers) {
		final List<Address> result = new ArrayList<Address>();
		for (final Iterator<Address> i = oldMembers.iterator(); i.hasNext();) {
			final Address addr1 = i.next();
			if (!newMembers.contains(addr1))
				result.add(addr1);
		}
		return result;
	}

	@Override
	protected void handleViewAccepted(View view) {
		if (oldView == null) {
			oldView = view;
			return;
		} else {
			final List departed = memberDiff(oldView.getMembers(), view.getMembers());
			if (departed.size() > 0) {
				for (final Iterator i = departed.iterator(); i.hasNext();) {
					final Address addr = (Address) i.next();
					final Client client = getClientForAddress(addr);
					if (client != null)
						handleDisconnectInThread(client);
				}
			}
			oldView = view;
		}
	}

	private void handleDisconnectInThread(final Client client) {
		final Thread t = new Thread(new Runnable() {
			public void run() {
				JGroupsManagerConnection.this.getEventHandler().handleDisconnectEvent(new DisconnectEvent(client,
						new Exception("client=" + client.clientID + " disconnected"), null));
			}
		});
		t.start();
	}

	private final Map<Address, Client> addressClientMap = Collections.synchronizedMap(new HashMap<Address, Client>());

	private void addClientToMap(Address address, Client client) {
		addressClientMap.put(address, client);
	}

	private void removeClientFromMap(Address addr) {
		addressClientMap.remove(addr);
	}

	/**
	 * @param addr
	 * @return
	 */
	private Client getClientForAddress(Address addr) {
		return addressClientMap.get(addr);
	}

}