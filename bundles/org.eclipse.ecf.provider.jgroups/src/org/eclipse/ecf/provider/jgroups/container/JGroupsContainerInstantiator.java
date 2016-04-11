package org.eclipse.ecf.provider.jgroups.container;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.generic.SOContainerConfig;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsID;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.remoteservice.provider.RemoteServiceContainerInstantiator;
import org.jgroups.JChannel;

public class JGroupsContainerInstantiator extends RemoteServiceContainerInstantiator {

	public JGroupsContainerInstantiator(String exporter, String[] importers) {
		super();
		exporterConfigs.add(exporter);
		exporterConfigToImporterConfigs.put(exporter, Arrays.asList(importers));
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
			return createJGroupsContainer(newID, null, channel, description.isServer());
		} catch (final Exception e) {
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

	protected IContainer createJGroupsContainer(JGroupsID newID, Map<String, ?> parameters, JChannel channel,
			boolean server) throws ECFException {
		if (server) {
			newID = (JGroupsID) getIDParameterValue(JGroupsNamespace.INSTANCE, parameters,
					JGroupsManagerContainer.JGROUPS_MANAGERID_PROP, JGroupsManagerContainer.JGROUPS_MANAGER_ID_DEFAULT);
			JGroupsManagerContainer manager = new JGroupsManagerContainer(new SOContainerConfig(newID), channel);
			manager.start();
			return manager;
		} else {
			newID = (JGroupsID) getIDParameterValue(JGroupsNamespace.INSTANCE, parameters,
					JGroupsClientContainer.JGROUPS_CLIENTID_PROP,
					JGroupsNamespace.SCHEME + ":" + UUID.randomUUID().toString());
			return new JGroupsClientContainer(new SOContainerConfig(newID), channel);
		}
	}

	protected JChannel getChannelFromParameters(Map<String, ?> parameters, boolean server) throws Exception {
		URL configURL = getParameterValue(parameters,
				server ? JGroupsManagerContainer.JGROUPS_MANAGER_CHANNEL_CONFIG_URL
						: JGroupsClientContainer.JGROUPS_CLIENT_CHANNEL_CONFIG_URL,
				URL.class, null);
		if (configURL != null)
			return new JChannel(configURL);
		String configString = getParameterValue(parameters,
				server ? JGroupsManagerContainer.JGROUPS_MANAGER_CHANNEL_CONFIG_STRING
						: JGroupsClientContainer.JGROUPS_CLIENT_CHANNEL_CONFIG_STRING,
				String.class, null);
		if (configString != null)
			return new JChannel(configString);
		InputStream ins = getParameterValue(parameters,
				server ? JGroupsManagerContainer.JGROUPS_MANAGER_CHANNEL_CONFIG_INPUTSTREAM
						: JGroupsClientContainer.JGROUPS_CLIENT_CHANNEL_CONFIG_INPUTSTREAM,
				InputStream.class, null);
		if (ins != null)
			return new JChannel(ins);
		return null;
	}

	@Override
	public IContainer createInstance(ContainerTypeDescription description, Map<String, ?> parameters)
			throws ContainerCreateException {
		boolean server = description.isServer();
		try {
			return createJGroupsContainer(null, parameters, getChannelFromParameters(parameters, server),
					description.isServer());
		} catch (Exception e) {
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

}