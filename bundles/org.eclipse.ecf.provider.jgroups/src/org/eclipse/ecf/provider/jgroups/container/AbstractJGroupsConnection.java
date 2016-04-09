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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.internal.provider.jgroups.Activator;
import org.eclipse.ecf.provider.comm.AsynchEvent;
import org.eclipse.ecf.provider.comm.ConnectionEvent;
import org.eclipse.ecf.provider.comm.IConnectionListener;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.comm.ISynchAsynchEventHandler;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.remoteservice.util.ObjectSerializationUtil;
import org.eclipse.osgi.util.NLS;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ReceiverAdapter;
import org.jgroups.TimeoutException;
import org.jgroups.View;

public abstract class AbstractJGroupsConnection implements ISynchAsynchConnection {

	public static final int DEFAULT_BUFFER_SIZE = 4096;

	private JChannel channel;
	private boolean started = false;
	private final ISynchAsynchEventHandler eventHandler;
	private List connectionListeners = new ArrayList();

	private int bufferSize = DEFAULT_BUFFER_SIZE;

	protected ISynchAsynchEventHandler getEventHandler() {
		return eventHandler;
	}

	protected int getBufferSize() {
		return bufferSize;
	}

	protected void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	private final Receiver receiver = new ReceiverAdapter() {
		@Override
		public void receive(Message arg0) {
			handleJGroupsReceive(arg0);
		}

		@Override
		public void viewAccepted(View arg0) {
			handleViewAccepted(arg0);
		}
	};

	protected void fireListenersConnect(ConnectionEvent event) {
		List toNotify = null;
		synchronized (connectionListeners) {
			toNotify = new ArrayList(connectionListeners);
		}
		for (final Iterator i = toNotify.iterator(); i.hasNext();) {
			final IConnectionListener l = (IConnectionListener) i.next();
			l.handleConnectEvent(event);
		}
	}

	/**
	 * @param view
	 */
	protected abstract void handleViewAccepted(View view);

	protected void fireListenersDisconnect(ConnectionEvent event) {
		List toNotify = null;
		synchronized (connectionListeners) {
			toNotify = new ArrayList(connectionListeners);
		}
		for (final Iterator i = toNotify.iterator(); i.hasNext();) {
			final IConnectionListener l = (IConnectionListener) i.next();
			l.handleConnectEvent(event);
		}
	}

	public AbstractJGroupsConnection(ISynchAsynchEventHandler eventHandler, JChannel channel) {
		Assert.isNotNull(eventHandler);
		this.eventHandler = eventHandler;
		this.channel = channel;
	}

	protected void sendMessage(Object data) throws IOException {
		sendMessage(serializeToBytes(data));
	}

	protected void sendMessage(byte[] data) throws IOException {
		try {
			getChannel().send(null, data);
		} catch (Exception e) {
			IOException except = new IOException("Exception sending message");
			except.setStackTrace(e.getStackTrace());
			throw except;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.provider.comm.IAsynchConnection#sendAsynch(org.eclipse
	 * .ecf.core.identity.ID, byte[])
	 */
	public synchronized void sendAsynch(ID targetID, byte[] data) throws IOException {
		if (!isConnected())
			throw new IOException("not connected");
		if (targetID != null && !targetID.getNamespace().equals(JGroupsNamespace.INSTANCE))
			throw new IOException("targetID=" + targetID.getName() + " is not in JGroupsNamespace");
		try {
			sendMessage(new AsyncMessage(getLocalID(), (JGroupsID) targetID, data));
		} catch (final Exception e) {
			throw new IOException(e.getLocalizedMessage());
		}
	}

	protected Message createMessage(JGroupsID dest, byte[] data) {
		// return new Message((dest != null)?dest.toAddress():null,
		// snd.toAddress(), data);
		return new Message(dest == null ? null : dest.getAddress(), data);
	}

	private Object syncResponse;

	private Object getSyncResponse() {
		synchronized (this) {
			Object response = syncResponse;
			syncResponse = null;
			return response;
		}
	}

	protected void setSyncResponse(Object response) {
		synchronized (this) {
			this.syncResponse = response;
			this.notify();
		}
	}

	ObjectSerializationUtil osu = new ObjectSerializationUtil();

	byte[] serializeToBytes(Object obj) throws IOException {
		return osu.serializeToBytes(obj);
	}

	public synchronized Object sendSynch(ID receiver, byte[] data) throws IOException {
		Object result = null;
		if (receiver == null || !(receiver instanceof JGroupsID))
			throw new IOException("invalid receiver id for disconnect request");
		if (isActive())
			result = sendMessageAndWait(new DisconnectRequestMessage((JGroupsID) receiver, getLocalID(), data), 3000);
		return result;
	}

	protected Object sendMessageAndWait(Object data, int timeout) throws IOException {
		long timeoutTime = System.currentTimeMillis() + timeout;
		Object response = null;
		synchronized (this) {
			sendMessage(data);
			while (System.currentTimeMillis() < timeoutTime) {
				response = getSyncResponse();
				if (response != null)
					break;
				try {
					wait(timeout / 10);
				} catch (InterruptedException e) {
					throw new IOException("sendMessageAndWait timed out timeout=" + timeout);
				}
			}
			if (response == null)
				throw new TimeoutException("Timed out sending");
		}
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.core.comm.IConnection#addCommEventListener(org.eclipse
	 * .ecf.core.comm.IConnectionListener)
	 */
	public void addListener(IConnectionListener listener) {
		connectionListeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.core.comm.IConnection#removeCommEventListener(org.eclipse
	 * .ecf.core.comm.IConnectionListener)
	 */
	public void removeListener(IConnectionListener listener) {
		connectionListeners.remove(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.provider.comm.IConnection#connect(org.eclipse.ecf.core
	 * .identity.ID, java.lang.Object, int)
	 */
	public abstract Object connect(ID targetID, Object data, int timeout) throws ECFException;

	protected void logMessageError(String errorString, Message message) {
		logMessageError(errorString, message, null);
	}

	protected void logMessageError(String errorString, Message message, Throwable t) {
		final String messageError = NLS.bind("jgroups message receive error.  error=%1 message=%2", errorString,
				message);
		Activator.getDefault().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, messageError, t));
	}

	protected void logException(String errorString, Throwable t) {
		Activator.getDefault().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, errorString, t));
	}

	protected void handleJGroupsReceive(final Message message) {
		if (message == null) {
			logMessageError("handleJGroupsReceive:message is null", message);
			return;
		}
		final Address src = message.getSrc();
		if (src == null) {
			logMessageError("handleJGroupsReceive:src address is not valid", message);
			return;
		}
		AbstractMessage o = null;
		try {
			o = (AbstractMessage) new ObjectSerializationUtil().deserializeFromBytes(message.getBuffer());
		} catch (Exception e1) {
			logMessageError("handleJGroupsReceive: could not deserialize message buffer", message, e1);
			return;
		}
		if (o == null) {
			logMessageError("object in message is null", message);
			return;
		}
		JGroupsID fromID = o.getFromID();
		if (fromID == null) {
			logMessageError("handleJGroupsReceive: fromID is null", message);
			return;
		}
		JGroupsID localID = getLocalID();
		JGroupsID targetID = o.getTargetID();
		// Handle SyncMessages
		if (o instanceof SyncMessage) {
			SyncMessage sm = (SyncMessage) o;
			if (localID.equals(targetID) && !fromID.equals(localID))
				handleSyncMessage(sm);
			return;
		}
		// Handle AsyncMessages
		if (o instanceof AsyncMessage) {
			final AsyncMessage msg = (AsyncMessage) o;
			if (!localID.equals(fromID) && (targetID == null || getLocalID().equals(targetID)))
				try {
					eventHandler.handleAsynchEvent(new AsynchEvent(this, msg.getData()));
				} catch (final IOException e) {
					logMessageError("handleJGroupsReceive", message, e);
				}
		}
	}

	protected abstract void handleSyncMessage(SyncMessage sm);

	protected Address getLocalAddress() {
		return channel.getAddress();
	}

	protected Channel getChannel() {
		return channel;
	}

	protected void setupJGroups(JGroupsID targetID) throws ECFException {
		try {
			if (channel == null)
				channel = new JChannel();
			JGroupsID localID = getLocalID();
			channel.setName(localID.getName());
			channel.setReceiver(receiver);
			channel.connect(targetID.getChannelName());
			localID.setAddress(channel.getAddress());
		} catch (final Exception e) {
			ECFException t = new ECFException("JGroups channel creation exception", e);
			t.setStackTrace(e.getStackTrace());
			throw t;
		}
	}

	public synchronized void disconnect() {
		stop();
		if (channel != null) {
			channel.disconnect();
			channel.close();
			channel = null;
		}
	}

	public JGroupsID getLocalID() {
		return (JGroupsID) eventHandler.getEventHandlerID();
	}

	public Map getProperties() {
		return null;
	}

	public synchronized boolean isConnected() {
		return (channel != null && channel.isConnected());
	}

	protected synchronized boolean isActive() {
		return isConnected() && isStarted();
	}

	public boolean isStarted() {
		return started;
	}

	public void start() {
		started = true;
	}

	public void stop() {
		started = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		return null;
	}

}