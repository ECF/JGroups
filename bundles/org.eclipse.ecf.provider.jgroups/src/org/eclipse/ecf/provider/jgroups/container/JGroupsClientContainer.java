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
import java.util.Map;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.internal.provider.jgroups.Messages;
import org.eclipse.ecf.internal.provider.jgroups.connection.AbstractJGroupsConnection;
import org.eclipse.ecf.internal.provider.jgroups.connection.JGroupsClientConnection;
import org.eclipse.ecf.presence.IIMMessageListener;
import org.eclipse.ecf.presence.chatroom.ChatRoomCreateException;
import org.eclipse.ecf.presence.chatroom.IChatRoomAdminListener;
import org.eclipse.ecf.presence.chatroom.IChatRoomAdminSender;
import org.eclipse.ecf.presence.chatroom.IChatRoomContainer;
import org.eclipse.ecf.presence.chatroom.IChatRoomInfo;
import org.eclipse.ecf.presence.chatroom.IChatRoomInvitationListener;
import org.eclipse.ecf.presence.chatroom.IChatRoomInvitationSender;
import org.eclipse.ecf.presence.chatroom.IChatRoomManager;
import org.eclipse.ecf.presence.chatroom.IChatRoomMessageSender;
import org.eclipse.ecf.presence.chatroom.IChatRoomParticipantListener;
import org.eclipse.ecf.presence.history.IHistoryManager;
import org.eclipse.ecf.presence.im.IChatMessageSender;
import org.eclipse.ecf.provider.comm.ConnectionCreateException;
import org.eclipse.ecf.provider.comm.ISynchAsynchConnection;
import org.eclipse.ecf.provider.generic.ClientSOContainer;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.jgroups.Channel;

/**
 * Trivial container implementation. that container adapter implementations can
 * be provided by the container class to expose appropriate adapters.
 */
public class JGroupsClientContainer extends ClientSOContainer implements IChatRoomManager, IChatRoomInvitationSender, IChatRoomContainer {

	private ArrayList<IChatRoomInvitationListener> invitationListeners;

	protected ID localID = null;
	protected ID targetID = null;

	private Map channels;

	public JGroupsClientContainer(SOContainerConfig config)
			throws IDCreateException {
		super(config);
		this.localID = config.getID();
	}

	@Override
	public Namespace getConnectNamespace() {
		return IDFactory.getDefault().getNamespaceByName(JGroupsNamespace.NAME);
	}

	@Override
	protected ISynchAsynchConnection createConnection(ID remoteSpace,
			Object data) throws ConnectionCreateException {
		this.targetID = remoteSpace;
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

	public void addInvitationListener(IChatRoomInvitationListener listener) {
		if (listener != null) {
			synchronized (invitationListeners) {
				if (!invitationListeners.contains(listener)) {
					invitationListeners.add(listener);
				}
			}
		}
	}

	public void removeInvitationListener(IChatRoomInvitationListener listener) {
		if (listener != null) {
			synchronized (invitationListeners) {
				invitationListeners.remove(listener);
			}
		}
	}

	protected void doSendChannelMessage(String msg) {
		if (connection != null) {
				try {
					connection.sendAsynch(this.localID, msg.getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
//				showMessage(channelName, ircUser, msg);
		}
	}

	public IChatRoomInvitationSender getInvitationSender() {
		return this;
	}

	public IChatRoomManager getParent() {
		return null;
	}

	public IChatRoomManager[] getChildren() {
		return new IChatRoomManager[0];
	}

	public IChatRoomInfo getChatRoomInfo(final String roomName) {
		if (roomName == null)
			return new IChatRoomInfo() {
				public IChatRoomContainer createChatRoomContainer()
						throws ContainerCreateException {
					return JGroupsClientContainer.this;
				}

				public ID getConnectedID() {
					return JGroupsClientContainer.this.getConnectedID();
				}

				public String getDescription() {
					return ""; //$NON-NLS-1$
				}

				public String getName() {
					return JGroupsClientContainer.this.getConnectedID().getName();
				}

				public int getParticipantsCount() {
					return 0;
				}
				// manager ID ?
				public ID getRoomID() {
					return JGroupsClientContainer.this.getConnectedID();
				}
				// groups topic
				public String getSubject() {
					return ""; //$NON-NLS-1$
				}

				public boolean isModerated() {
					return false;
				}

				public boolean isPersistent() {
					return false;
				}

				public boolean requiresPassword() {
					return false;
				}

				public Object getAdapter(Class adapter) {
					return null;
				}
			};
		return new IChatRoomInfo() {
			public IChatRoomContainer createChatRoomContainer()
					throws ContainerCreateException {
				try {
					
					JGroupsChannelContainer newChannelContainer = new JGroupsChannelContainer(
							JGroupsClientContainer.this, this.getRoomID());
					
					addChannel(roomName, newChannelContainer);
					
					return newChannelContainer;
					
				} catch (Exception e) {
					throw new ContainerCreateException(
							Messages.JGroupsClientContainer_Exception_Create_ChatRoom,
							e);
				}
			}

			public ID getConnectedID() {
				return JGroupsClientContainer.this.getConnectedID();
			}

			public String getDescription() {
				return ""; //$NON-NLS-1$
			}

			// TODO
			public String getName() {
				return roomName;
			}

			public int getParticipantsCount() {
				return 0;
			}

			public ID getRoomID() {
				return JGroupsClientContainer.this.localID;
			}

			public String getSubject() {
				return ""; //$NON-NLS-1$
			}

			public boolean isModerated() {
				return false;
			}

			public boolean isPersistent() {
				return false;
			}

			public boolean requiresPassword() {
				return false;
			}

			public Object getAdapter(Class adapter) {
				return null;
			}
		};
	}

	public IChatRoomInfo[] getChatRoomInfos() {
		return new IChatRoomInfo[0];
	}

	public IChatRoomInfo createChatRoom(String roomName, Map properties)
			throws ChatRoomCreateException {
		// TODO Auto-generated method stub
		return null;
	}

	public IHistoryManager getHistoryManager() {
		// TODO Auto-generated method stub
		return null;
	}

	public void sendInvitation(ID room, ID targetUser, String subject,
			String body) throws ECFException {
		// TODO Auto-generated method stub
		
	}

	public void addMessageListener(IIMMessageListener listener) {
		// TODO Auto-generated method stub
		
	}

	public void removeMessageListener(IIMMessageListener listener) {
		// TODO Auto-generated method stub
		
	}

	public IChatMessageSender getPrivateMessageSender() {
		// TODO Auto-generated method stub
		return null;
	}

	public IChatRoomMessageSender getChatRoomMessageSender() {
		// TODO Auto-generated method stub
		return null;
	}

	public void addChatRoomParticipantListener(
			IChatRoomParticipantListener participantListener) {
		// TODO Auto-generated method stub
		
	}

	public void removeChatRoomParticipantListener(
			IChatRoomParticipantListener participantListener) {
		// TODO Auto-generated method stub
		
	}

	public void addChatRoomAdminListener(IChatRoomAdminListener adminListener) {
		// TODO Auto-generated method stub
		
	}

	public void removeChatRoomAdminListener(IChatRoomAdminListener adminListener) {
		// TODO Auto-generated method stub
		
	}

	public IChatRoomAdminSender getChatRoomAdminSender() {
		// TODO Auto-generated method stub
		return null;
	}

	public ID[] getChatRoomParticipants() {
		// TODO Auto-generated method stub
		return null;
	}

	
	// helper
	
	protected void addChannel(String channel, JGroupsChannelContainer container) {
		channels.put(channel, container);
	}

}
