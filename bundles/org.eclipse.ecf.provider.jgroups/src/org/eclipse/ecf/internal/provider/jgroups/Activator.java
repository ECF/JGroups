/*******************************************************************************
 * Copyright (c) 2007 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups;

import java.net.URL;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ecf.core.util.LogHelper;
import org.osgi.framework.Bundle;
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

	private static final String STACK_CONFIG_EPOINT = Activator.PLUGIN_ID
			+ ".stackConfig";
	private static final String STACK_CONFIG_ID_ATTRIBUTE = "id";
	private static final String STACK_CONFIG_FILE_ATTRIBUTE = "configFile";

	public static final String STACK_CONFIG_ID = Activator.PLUGIN_ID
			+ ".default";

	// The shared instance
	private static Activator plugin;

	public BundleContext getContext() {
		return context;
	}

	private BundleContext context;

	private ServiceTracker logServiceTracker = null;

	private ServiceTracker extensionRegistryTracker = null;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		plugin = this;
		this.context = context;
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
		if (extensionRegistryTracker != null) {
			extensionRegistryTracker.close();
			extensionRegistryTracker = null;
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
			logService.log(LogHelper.getLogCode(status), LogHelper
					.getLogMessage(status), status.getException());
		}
	}

	protected LogService getLogService() {
		if (logServiceTracker == null) {
			logServiceTracker = new ServiceTracker(this.context,
					LogService.class.getName(), null);
			logServiceTracker.open();
		}
		return (LogService) logServiceTracker.getService();
	}

	private IExtensionRegistry getExtensionRegistry() {
		if (extensionRegistryTracker == null) {
			extensionRegistryTracker = new ServiceTracker(this.context,
					IExtensionRegistry.class.getName(), null);
			extensionRegistryTracker.open();
		}
		return (IExtensionRegistry) extensionRegistryTracker.getService();
	}

	public URL getConfigURLForStackID(String stackID) {
		if (stackID == null || context == null)
			return null;
		final IConfigurationElement configElement = findConfigurationElementForStackID(stackID);
		if (configElement == null)
			return null;
		final String bundleSymbolicName = configElement.getContributor()
				.getName();
		final String stackConfigFile = configElement
				.getAttribute(STACK_CONFIG_FILE_ATTRIBUTE);
		if (stackConfigFile == null || stackConfigFile.equals(""))
			return null;
		final Bundle[] bundles = context.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			if (bundles[i].getSymbolicName().equals(bundleSymbolicName))
				return bundles[i].getResource(stackConfigFile);
		}
		return null;
	}

	/**
	 * @param stackID
	 * @return
	 */
	private IConfigurationElement findConfigurationElementForStackID(
			String stackID) {
		final IExtensionRegistry extensionRegistry = Activator.getDefault()
				.getExtensionRegistry();
		if (extensionRegistry == null)
			return null;
		final IExtensionPoint extensionPoint = extensionRegistry
				.getExtensionPoint(STACK_CONFIG_EPOINT);
		if (extensionPoint == null) {
			return null;
		}
		final IConfigurationElement configurationElements[] = extensionPoint
				.getConfigurationElements();
		for (int i = 0; i < configurationElements.length; i++) {
			final String idAttribute = configurationElements[i]
					.getAttribute(STACK_CONFIG_ID_ATTRIBUTE);
			if (idAttribute != null && idAttribute.equals(stackID))
				return configurationElements[i];
		}
		return null;
	}

}
