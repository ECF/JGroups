/****************************************************************************
 * Copyright (c) 2004, 2007 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

import org.eclipse.ecf.core.AbstractContainer;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.events.ContainerConnectedEvent;
import org.eclipse.ecf.core.events.ContainerConnectingEvent;
import org.eclipse.ecf.core.events.ContainerDisconnectedEvent;
import org.eclipse.ecf.core.events.ContainerDisconnectingEvent;
import org.eclipse.ecf.core.events.IContainerEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.identity.StringID;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.ecf.core.user.User;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.internal.provider.jgroups.Messages;
import org.eclipse.ecf.presence.IIMMessageListener;
import org.eclipse.ecf.presence.IPresence;
import org.eclipse.ecf.presence.chatroom.IChatRoomAdminListener;
import org.eclipse.ecf.presence.chatroom.IChatRoomAdminSender;
import org.eclipse.ecf.presence.chatroom.IChatRoomContainer;
import org.eclipse.ecf.presence.chatroom.IChatRoomMessageSender;
import org.eclipse.ecf.presence.chatroom.IChatRoomParticipantListener;
import org.eclipse.ecf.presence.im.IChatMessageSender;
import org.eclipse.osgi.util.NLS;

/**
 * IContainer class used to represent a specific IRC channel (e.g. #eclipse-dev)
 * 
 */
public class JGroupsChannelContainer extends AbstractContainer implements
		IChatMessageSender, IChatRoomContainer {

	private static final long CONNECT_TIMEOUT = new Long(System.getProperty(
			"org.eclipse.ecf.provider.irc.connectTimeout", "30000"))
			.longValue();

	protected List participantListeners = new ArrayList();
	protected JGroupsClientContainer jgContainer;
	protected User ircUser = null;
	protected String channelOperator;

	protected Object connectLock = new Object();
	protected boolean connectWaiting = false;

	protected Vector channelParticipants = new Vector();

	protected IChatRoomAdminSender adminSender = null;

	protected IChatRoomMessageSender sender = new IChatRoomMessageSender() {
		public void sendMessage(String message) throws ECFException {
			jgContainer.doSendChannelMessage(message);
		}
	};

	private ID localID;

	public JGroupsChannelContainer(JGroupsClientContainer container, ID localID) {
		this.jgContainer = container;
		this.localID = localID;
	}

	public void addChatRoomParticipantListener(
			IChatRoomParticipantListener participantListener) {
		participantListeners.add(participantListener);
	}

	public void removeChatRoomParticipantListener(
			IChatRoomParticipantListener participantListener) {
		participantListeners.remove(participantListener);
	}

	// protected void handleUserQuit(String name) {
	// if (containsChannelParticipant(createIDFromString(name)) != null)
	// firePresenceListeners(false, new String[] { name });
	// }

	private IPresence createPresence(final boolean available) {
		return new IPresence() {

			private static final long serialVersionUID = -7514227760059471898L;
			Map properties = new HashMap();

			public Mode getMode() {
				return (available ? IPresence.Mode.AVAILABLE
						: IPresence.Mode.AWAY);
			}

			public Map getProperties() {
				return properties;
			}

			public String getStatus() {
				return null;
			}

			public Type getType() {
				return (available ? IPresence.Type.AVAILABLE
						: IPresence.Type.UNAVAILABLE);
			}

			public Object getAdapter(Class adapter) {
				return null;
			}

			public byte[] getPictureData() {
				return new byte[0];
			}
		};
	}

	protected boolean addChannelParticipant(ID participantID) {
		if (containsChannelParticipant(participantID) == null) {
			channelParticipants.add(participantID);
			return true;
		}
		return false;
	}

	protected ID removeChannelParticipant(ID participantID) {
		if (channelParticipants.remove(participantID))
			return participantID;
		return null;
	}

	protected ID containsChannelParticipant(ID participantID) {
		if (channelParticipants.contains(participantID))
			return participantID;
		return null;
	}

	protected void firePresenceListeners(boolean joined, String[] users) {
		for (int j = 0; j < users.length; j++) {
			if (joined) {
				if (isChannelOperator(users[j]))
					setChannelOperator(users[j]);
				ID participantID = createIDFromString(users[j]);
				if (addChannelParticipant(participantID)) {
					// Notify all listeners
					for (Iterator i = participantListeners.iterator(); i
							.hasNext();) {
						IChatRoomParticipantListener l = (IChatRoomParticipantListener) i
								.next();

						l.handleArrived(new User(participantID));
						l.handlePresenceUpdated(participantID,
								createPresence(true));
					}
				}
			} else {
				ID removeID = removeChannelParticipant(createIDFromString(users[j]));
				if (removeID != null) {
					// Notify all listeners
					for (Iterator i = participantListeners.iterator(); i
							.hasNext();) {
						IChatRoomParticipantListener l = (IChatRoomParticipantListener) i
								.next();

						l.handlePresenceUpdated(removeID, createPresence(false));
						l.handleDeparted(new User(removeID));
					}

				}
			}
		}
	}

	private ID createIDFromString(String id) {
		return IDFactory.getDefault().createStringID(id);
	}

	protected boolean isChannelOperator(String user) {
		return false;
	}

	public IChatRoomMessageSender getChatRoomMessageSender() {
		return sender;
	}

	protected String getIRCUserName(User user) {
		return user == null ? null : user.toString();
	}

	protected void setIRCUser(User user) {
		if (this.ircUser == null) {
			this.ircUser = user;
			synchronized (connectLock) {
				if (connectWaiting) {
					connectWaiting = false;
					connectLock.notify();
				}
			}
		} else
			firePresenceListeners(true, new String[] { getIRCUserName(user) });
	}

	protected void fireContainerEvent(IContainerEvent event) {
		super.fireContainerEvent(event);
	}

	public void connect(ID connectID, IConnectContext connectContext)
			throws ContainerConnectException {
		// Actually do join here
		if (connectID == null)
			throw new ContainerConnectException(
					Messages.JGroupsClientChannel_CONNECT_EXCEPTION_CONNECT_ERROR);
		if (connectWaiting)
			throw new ContainerConnectException(
					Messages.jGroupsClientChannel_CONNECT_EXCEPTION_CONNECT_WAITING);
		// Get channel name
		String channelName = connectID.getName();
		fireContainerEvent(new ContainerConnectingEvent(this.getID(),
				connectID, connectContext));
		// Get password via callback in connectContext
		String pw = getPasswordFromConnectContext(connectContext);
		this.jgContainer.connect(connectID, connectContext);
	}

	public void disconnect() {
		this.jgContainer.disconnect();
	}

	public ID getConnectedID() {
		return this.localID;
	}

	public ID getID() {
		return this.localID;
	}

	public Object getAdapter(Class serviceType) {
		if (serviceType != null && serviceType.isInstance(this)) {
			return this;
		}
		return null;
	}

	public Namespace getConnectNamespace() {
		return IDFactory.getDefault().getNamespaceByName(
				StringID.class.getName());
	}

	protected void setChannelOperator(String channelOperator) {
		this.channelOperator = channelOperator;
	}

	public void sendChatMessage(ID toID, String msg) throws ECFException {
		jgContainer.doSendChannelMessage(msg);
	}

	public IChatMessageSender getPrivateMessageSender() {
		return this;
	}

	public ID[] getChatRoomParticipants() {
		return (ID[]) channelParticipants.toArray(new ID[channelParticipants
				.size()]);
	}

	public IChatRoomAdminSender getChatRoomAdminSender() {
		synchronized (this) {
			if (adminSender == null) {
				adminSender = new IChatRoomAdminSender() {
					public void sendSubjectChange(String newsubject)
							throws ECFException {
						jgContainer.doSendChannelMessage(newsubject);
					}
				};
			}
		}
		return adminSender;
	}

	public void addMessageListener(IIMMessageListener listener) {
		// TODO Auto-generated method stub
		
	}

	public void removeMessageListener(IIMMessageListener listener) {
		// TODO Auto-generated method stub
		
	}

	public void addChatRoomAdminListener(IChatRoomAdminListener adminListener) {
		// TODO Auto-generated method stub
		
	}

	public void removeChatRoomAdminListener(IChatRoomAdminListener adminListener) {
		// TODO Auto-generated method stub
		
	}

	public void sendChatMessage(ID toID, ID threadID,
			org.eclipse.ecf.presence.im.IChatMessage.Type type, String subject,
			String body, Map properties) throws ECFException {
		// TODO Auto-generated method stub
		
	}
}
