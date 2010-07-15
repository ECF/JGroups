package org.eclipse.ecf.internal.tests.provider.jgroups;

import java.net.URL;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends Plugin {

	private BundleContext context;

	private ServiceTracker extensionRegistryTracker = null;

	private static final String STACK_CONFIG_ID_ATTRIBUTE = "id";

	private static final String STACK_CONFIG_EPOINT = "org.eclipse.ecf.provider.jgroups.stackConfig";

	public static final String STACK_CONFIG_ID = "org.eclipse.ecf.provider.jgroups.default";

	private static final String STACK_CONFIG_FILE_ATTRIBUTE = "configFile";

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.ecf.tests.provider.jgroups";

	// The shared instance
	private static Activator plugin;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		this.context = context;
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
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

	private IExtensionRegistry getExtensionRegistry() {
		if (extensionRegistryTracker == null) {
			extensionRegistryTracker = new ServiceTracker(this.context,
					IExtensionRegistry.class.getName(), null);
			extensionRegistryTracker.open();
		}
		return (IExtensionRegistry) extensionRegistryTracker.getService();
	}

}
