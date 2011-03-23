/****************************************************************************
 * Copyright (c) 2004 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups.ui;

import org.eclipse.ecf.core.IContainer;

public class ClientEntry {
	IContainer container;
	String containerType;
	boolean isDisposed = false;

	public ClientEntry(String type, IContainer cont) {
		this.containerType = type;
		this.container = cont;
	}

	public IContainer getContainer() {
		return container;
	}

	public String getContainerType() {
		return containerType;
	}


	public boolean isDisposed() {
		return isDisposed;
	}

	public void dispose() {
		isDisposed = true;
	}
}