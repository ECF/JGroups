package org.eclipse.ecf.client.jgroups;

import org.eclipse.ecf.core.IContainerManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

	@SuppressWarnings("unused")
	private static final String PLUGIN_ID = "org.eclipse.ecf.client.jgroups";
	private static Activator plugin;
	@SuppressWarnings("unused")
	private BundleContext context;
	private ServiceTracker containerManagerTracker;

	@SuppressWarnings({ })
	public void start(BundleContext context) throws Exception {
		this.context = context;
		plugin = this;
		if (containerManagerTracker == null) {
			containerManagerTracker = new ServiceTracker(context,
					IContainerManager.class.getName(), null);
			containerManagerTracker.open();
		}
	}

	public void stop(BundleContext context) throws Exception {
		if (containerManagerTracker != null) {
			containerManagerTracker.close();
			containerManagerTracker = null;
		}
		plugin = null;
		this.context = null;
	}

	public static Activator getDefault() {
		if (plugin == null)
			plugin = new Activator();
		return Activator.plugin;
	}

	public IContainerManager getContainerManager() {
		return (IContainerManager) containerManagerTracker.getService();
	}

}
