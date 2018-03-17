/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.jgroups.container;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.provider.ContainerIntentException;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.remoteservice.provider.RemoteServiceContainerInstantiator;
import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.conf.ConfiguratorFactory;
import org.jgroups.conf.ProtocolStackConfigurator;

public class JGroupsContainerInstantiator extends RemoteServiceContainerInstantiator {

	public static final String JGROUPS_MANAGER_CONFIG = "ecf.jgroups.manager";
	public static final String JGROUPS_CLIENT_CONFIG = "ecf.jgroups.client";

	public static final String JGROUPS_ID_PROP = "id";
	public static final String JGROUPS_MANAGER_ID_DEFAULT = JGroupsNamespace.INSTANCE.getScheme()
			+ ":ecf.jgroups.defaultGroup";
	public static final String JGROUPS_CHANNEL_CONFIG_STRING = "channelConfigProperty";
	
	public JGroupsContainerInstantiator() {
		super();
		exporterConfigs.add(JGROUPS_MANAGER_CONFIG);
		exporterConfigs.add(JGROUPS_CLIENT_CONFIG);
		exporterConfigToImporterConfigs.put(JGROUPS_MANAGER_CONFIG,
				Arrays.asList(new String[] { JGROUPS_CLIENT_CONFIG }));
		exporterConfigToImporterConfigs.put(JGROUPS_CLIENT_CONFIG,
				Arrays.asList(new String[] { JGROUPS_CLIENT_CONFIG, JGROUPS_MANAGER_CONFIG }));
	}

	@Override
	protected boolean supportsOSGIAsyncIntent(ContainerTypeDescription description) {
		return true;
	}
	
	@Override
	public IContainer createInstance(ContainerTypeDescription description, Object[] parameters)
			throws ContainerCreateException {
		try {
			JGroupsID newID = null;
			JChannel channel = null;
			if (parameters != null) {
				for (int i = 0; i < parameters.length; i++) {
					if (parameters[i] instanceof JGroupsID)
						newID = (JGroupsID) parameters[i];
					else if (parameters[i] instanceof String)
						newID = (JGroupsID) IDFactory.getDefault().createID(JGroupsNamespace.NAME,
								(String) parameters[i]);
					else if (parameters[i] instanceof JChannel)
						channel = (JChannel) parameters[i];
				}
			}
			if (newID == null)
				return super.createInstance(description, parameters);
			return createJGroupsContainer(description, newID, null, channel);
		} catch (final Exception e) {
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

	protected IContainer createJGroupsContainer(ContainerTypeDescription description, JGroupsID newID, Map<String, ?> parameters, JChannel channel) throws ECFException {
		// If jgroups bindAddress is set use that otherwise localhost
		String hostname = System.getProperty("jgroups.bind_addr","localhost");
		URI uri = null;
		try {
			uri = new URI("jgroups://"+hostname);
		} catch (Exception e) {
			// won't happen
		}
		// Now check intents
		checkOSGIIntents(description, uri, (parameters==null)?new HashMap<String,Object>():parameters);
		// If passed then return appropriate container instance
		if (description.isServer()) {
			newID = (JGroupsID) getIDParameterValue(JGroupsNamespace.INSTANCE, parameters, JGROUPS_ID_PROP,
					JGROUPS_MANAGER_ID_DEFAULT);
			JGroupsManagerContainer manager = new JGroupsManagerContainer(new SOContainerConfig(newID), channel);
			manager.start();
			return manager;
		} else {
			newID = (JGroupsID) getIDParameterValue(JGroupsNamespace.INSTANCE, parameters, JGROUPS_ID_PROP,
					JGroupsNamespace.SCHEME + ":" + UUID.randomUUID().toString());
			return new JGroupsClientContainer(new SOContainerConfig(newID), channel);
		}
	}

	protected JChannel getChannelFromParameters(ContainerTypeDescription description, Map<String, ?> parameters) throws Exception {
		ProtocolStackConfigurator configurator = null;
		String configString = getParameterValue(parameters, JGROUPS_CHANNEL_CONFIG_STRING, String.class, null);
		if (configString != null) 
			configurator = ConfiguratorFactory.getStackConfigurator(configString);
		configurator = ConfiguratorFactory.getStackConfigurator(Global.DEFAULT_PROTOCOL_STACK);
		return new JChannel(configurator);
	}

	@Override
	public IContainer createInstance(ContainerTypeDescription description, Map<String, ?> parameters)
			throws ContainerCreateException {
		try {
			return createJGroupsContainer(description, null, parameters, getChannelFromParameters(description, parameters));
		} catch (Exception e) {
			if (e instanceof ContainerIntentException) 
				throw (ContainerIntentException) e;
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

}