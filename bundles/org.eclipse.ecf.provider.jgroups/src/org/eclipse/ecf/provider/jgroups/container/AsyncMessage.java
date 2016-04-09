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

public class AsyncMessage extends AbstractMessage {

	private static final long serialVersionUID = -7861937059730245915L;

	AsyncMessage(JGroupsID fromID, JGroupsID targetID, byte[] data) {
		super(fromID, targetID, data);
	}

}
