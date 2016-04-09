/*******************************************************************************
 * Copyright (c) 2016 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.identity;

import java.net.URI;

import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.identity.URIID;
import org.jgroups.Address;

public class JGroupsID extends URIID {
	private static final long serialVersionUID = 8221231856444089704L;

	private Address address;

	public JGroupsID(Namespace ns, URI id) throws IDCreateException {
		super(ns, id);
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	public String getChannelName() {
		return toURI().getSchemeSpecificPart();
	}

	public String toString() {
		final StringBuffer buf = new StringBuffer("JGroupsID[");
		buf.append(getName()).append("]");
		return buf.toString();
	}
}
