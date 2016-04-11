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

package org.eclipse.ecf.internal.tests.provider.jgroups;

import org.eclipse.ecf.provider.jgroups.container.JGroupsClientContainer;
import org.eclipse.ecf.provider.jgroups.container.JGroupsManagerContainer;

public interface JGroups {

	public static final String CLIENT_CONTAINER_NAME = JGroupsClientContainer.JGROUPS_CLIENT_CONFIG;
	public static final String SERVER_CONTAINER_NAME = JGroupsManagerContainer.JGROUPS_MANAGER_CONFIG;
	public static final String TARGET_NAME = JGroupsManagerContainer.JGROUPS_MANAGER_ID_DEFAULT;
}
