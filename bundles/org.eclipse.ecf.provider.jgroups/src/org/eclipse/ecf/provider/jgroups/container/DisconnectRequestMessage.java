/*******************************************************************************
 * Copyright (c) 2016 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;

public class DisconnectRequestMessage extends SyncMessage {

	private static final long serialVersionUID = -6015814168731082696L;

	public DisconnectRequestMessage(JGroupsID fromID, JGroupsID targetID, byte[] data) {
		super(fromID, targetID, data);
	}

}
