/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups.connection;

import java.io.Serializable;

import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;

public class JGroupsMessage implements Serializable {

	private static final long serialVersionUID = -1835086159379451564L;

	private final JGroupsID target;

	private final JGroupsID sender;

	private final byte[] data;

	protected JGroupsMessage(JGroupsID sender, JGroupsID target, byte[] data) {
		this.sender = sender;
		this.target = target;
		this.data = data;
	}

	public Serializable getData() {
		return data;
	}

	public JGroupsID getTargetID() {
		return target;
	}

	public JGroupsID getSenderID() {
		return sender;
	}

	public String toString() {
		final StringBuffer buf = new StringBuffer("JGroupsMessage["); //$NON-NLS-1$
		buf.append(target).append(";").append(data).append("]"); //$NON-NLS-1$ //$NON-NLS-2$
		return buf.toString();
	}

}
