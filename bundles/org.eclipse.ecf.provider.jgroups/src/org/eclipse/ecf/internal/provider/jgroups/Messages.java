/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups;

import org.eclipse.osgi.util.NLS;

/**
 * 
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.ecf.internal.provider.jgroups.messages"; //$NON-NLS-1$
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_CONNECT_ERROR;
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_CONNECT_FAILED;
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_INVALID_RESPONSE;
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_NOT_SERIALIZABLE;
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_TARGET_NOT_JMSID;
	public static String JGroupsClientChannel_CONNECT_EXCEPTION_TARGET_REFUSED_CONNECTION;
	public static String jGroupsClientChannel_CONNECT_EXCEPTION_CONNECT_WAITING;
	public static String JGroupsServer_CONNECT_EXCEPTION_CONTAINER_CLOSING;
	public static String JGroupsServer_CONNECT_EXCEPTION_CONTAINER_MESSAGE_NOT_NULL;
	public static String JGroupsServer_CONNECT_EXCEPTION_JOINGROUPMESSAGE_NOT_NULL;
	public static String JGroupsServer_CONNECT_EXCEPTION_REFUSED;
	public static String JGroupsClientContainer_Exception_Create_ChatRoom;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
