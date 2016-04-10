package org.eclipse.ecf.provider.jgroups.container;

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

	public static final String JGROUPS_ID_PROP = "id";
	public static final String JGROUPS_CLIENTID_PROP = "clientId";
	public static final String JGROUPS_MANAGER_ID_DEFAULT = "jgroups:exampleGroup";
	public static final String JGROUPS_CHANNEL_CONFIG_URL = "channelConfigUrl";
	public static final String JGROUPS_CHANNEL_CONFIG_STRING = "channelConfigString";

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
			if (parameters != null && parameters.length > 0) {
				if (parameters[0] instanceof JGroupsID)
					newID = (JGroupsID) parameters[0];
				else if (parameters[0] instanceof String)
					newID = (JGroupsID) IDFactory.getDefault().createID(JGroupsNamespace.NAME, (String) parameters[0]);
			}
			if (newID == null)
				return super.createInstance(description, parameters);
			return createJGroupsContainer(description, newID, null, null);
		} catch (final Exception e) {
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

	protected IContainer createJGroupsContainer(ContainerTypeDescription description, JGroupsID newID,
			Map<String, ?> parameters, JChannel channel) throws ECFException {
		if (description.isServer()) {
			if (newID == null)
				newID = (JGroupsID) getIDParameterValue(JGroupsNamespace.INSTANCE, parameters, JGROUPS_ID_PROP,
						JGROUPS_MANAGER_ID_DEFAULT);
			JGroupsManagerContainer manager = new JGroupsManagerContainer(new SOContainerConfig(newID), channel);
			manager.start();
			return manager;
		} else {
			if (newID == null) {
				String newIDString = getParameterValue(parameters, JGROUPS_CLIENTID_PROP, null);
				if (newIDString == null)
					newIDString = JGroupsNamespace.SCHEME + ":" + UUID.randomUUID().toString();
				newID = (JGroupsID) JGroupsNamespace.INSTANCE.createInstance(new Object[] { newIDString });
			}
			return new JGroupsClientContainer(new SOContainerConfig(newID), channel);
		}
	}

	@Override
	public IContainer createInstance(ContainerTypeDescription description, Map<String, ?> parameters)
			throws ContainerCreateException {
		try {
			JChannel channel = null;
			URL configURL = getParameterValue(parameters, JGROUPS_CHANNEL_CONFIG_URL, URL.class, null);
			if (configURL != null)
				channel = new JChannel(configURL);
			String configString = getParameterValue(parameters, JGROUPS_CHANNEL_CONFIG_STRING, String.class, null);
			if (configURL != null)
				channel = new JChannel(configString);
			return createJGroupsContainer(description, null, parameters, channel);
		} catch (Exception e) {
			ContainerCreateException cce = new ContainerCreateException("Exception creating jgroups manager container",
					e);
			cce.setStackTrace(e.getStackTrace());
			throw cce;
		}
	}

}