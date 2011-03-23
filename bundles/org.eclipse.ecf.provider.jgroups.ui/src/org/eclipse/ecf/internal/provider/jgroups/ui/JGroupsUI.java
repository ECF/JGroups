package org.eclipse.ecf.internal.provider.jgroups.ui;

import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.util.IExceptionHandler;
import org.eclipse.ecf.presence.chatroom.IChatRoomManager;
import org.eclipse.ecf.presence.ui.chatroom.ChatRoomManagerUI;

public class JGroupsUI extends ChatRoomManagerUI {

	public JGroupsUI(IContainer container, IChatRoomManager manager) {
		super(container, manager);
	}

	public JGroupsUI(IContainer container, IChatRoomManager manager,
			IExceptionHandler exceptionHandler) {
		super(container, manager, exceptionHandler);
	}

	
}
