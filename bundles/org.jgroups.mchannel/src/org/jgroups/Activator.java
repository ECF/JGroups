package org.jgroups;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;


public class Activator implements BundleActivator {

		private static Activator plugin;
		private BundleContext context;
		
	public Activator() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void start(BundleContext context) throws Exception {
		plugin=this;
		this.context=context;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}
	
	

}
