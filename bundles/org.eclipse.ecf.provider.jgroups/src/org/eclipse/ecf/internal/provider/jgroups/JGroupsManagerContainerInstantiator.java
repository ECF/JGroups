/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.provider.generic.GenericContainerInstantiator;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.container.JGroupsManagerContainer;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;

/**
 *
 */
public class JGroupsManagerContainerInstantiator extends
		GenericContainerInstantiator {

	protected static final String JGROUPS_MANAGER_NAME = "ecf.jgroups.manager";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ecf.core.provider.BaseContainerInstantiator#createInstance
	 * (org.eclipse.ecf.core.ContainerTypeDescription, java.lang.Object[])
	 */
	public IContainer createInstance(ContainerTypeDescription description,
			Object[] parameters) throws ContainerCreateException {
		try {
			ID newID = null;
			if (parameters != null && parameters.length > 0) {
				if (parameters[0] instanceof JGroupsID)
					newID = (ID) parameters[0];
				else if (parameters[0] instanceof String)
					newID = IDFactory.getDefault().createID(
							JGroupsNamespace.NAME, (String) parameters[0]);
			}
			if (newID == null)
				throw new ContainerCreateException(
						"invalid parameters for creating jgroups manager instance");
			final JGroupsManagerContainer manager = new JGroupsManagerContainer(
					new SOContainerConfig(newID));
			manager.start();
			return manager;
		} catch (final Exception e) {
			throw new ContainerCreateException(
					"Exception creating jgroups manager container", e);
		}
	}

	public String[] getImportedConfigs(ContainerTypeDescription description,
			String[] exporterSupportedConfigs) {
		List results = new ArrayList();
		List supportedConfigs = Arrays.asList(exporterSupportedConfigs);
		// For a manager, if a client is exporter then we are an importer
		if (JGROUPS_MANAGER_NAME.equals(description.getName())) {
			if (supportedConfigs
					.contains(JGroupsClientContainerInstantiator.JGROUPS_CLIENT_NAME))
				results.add(JGROUPS_MANAGER_NAME);
		}
		if (results.size() == 0)
			return null;
		return (String[]) results.toArray(new String[] {});
	}

	public String[] getSupportedConfigs(ContainerTypeDescription description) {
		return new String[] { JGROUPS_MANAGER_NAME };
	}

}
