/*******************************************************************************
 * Copyright (c) 2016 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.core.util.LogHelper;
import org.eclipse.ecf.provider.jgroups.container.JGroupsClientContainer;
import org.eclipse.ecf.provider.jgroups.container.JGroupsContainerInstantiator;
import org.eclipse.ecf.provider.jgroups.container.JGroupsManagerContainer;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteServiceContainerAdapterFactory;
import org.eclipse.ecf.remoteservice.provider.AdapterConfig;
import org.eclipse.ecf.remoteservice.provider.IRemoteServiceDistributionProvider;
import org.eclipse.ecf.remoteservice.provider.RemoteServiceDistributionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator implements BundleActivator {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.ecf.provider.jgroups";

	// The shared instance
	private static Activator plugin;

	public BundleContext getContext() {
		return context;
	}

	private BundleContext context;

	private ServiceTracker logServiceTracker = null;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	public static final String JGROUPS_MANAGER_CONFIG = "ecf.jgroups.manager";
	public static final String JGROUPS_CLIENT_CONFIG = "ecf.jgroups.client";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		plugin = this;
		this.context = context;
		// register namespace
		this.context.registerService(Namespace.class, new JGroupsNamespace(), null);
		// Register JGroups Manager
		JGroupsContainerInstantiator instantiator = new JGroupsContainerInstantiator();
		context.registerService(IRemoteServiceDistributionProvider.class,
				new RemoteServiceDistributionProvider.Builder().setName(JGROUPS_MANAGER_CONFIG)
						.setInstantiator(instantiator).setDescription("ECF JGroups Manager").setServer(true)
						.setAdapterConfig(new AdapterConfig(new RemoteServiceContainerAdapterFactory(),
								JGroupsManagerContainer.class))
						.build(),
				null);
		// same with client
		context.registerService(IRemoteServiceDistributionProvider.class,
				new RemoteServiceDistributionProvider.Builder().setName("ecf.jgroups.client")
						.setInstantiator(instantiator).setDescription("ECF JGroups Client").setServer(false)
						.setAdapterConfig(new AdapterConfig(new RemoteServiceContainerAdapterFactory(),
								JGroupsClientContainer.class))
						.build(),
				null);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		if (logServiceTracker != null) {
			logServiceTracker.close();
			logServiceTracker = null;
		}
		this.context = null;
		plugin = null;
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	public void log(IStatus status) {
		final LogService logService = getLogService();
		if (logService != null) {
			logService.log(LogHelper.getLogCode(status), LogHelper.getLogMessage(status), status.getException());
		}
	}

	protected LogService getLogService() {
		if (logServiceTracker == null) {
			logServiceTracker = new ServiceTracker(this.context, LogService.class.getName(), null);
			logServiceTracker.open();
		}
		return (LogService) logServiceTracker.getService();
	}

}
