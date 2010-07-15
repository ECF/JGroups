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

package org.eclipse.ecf.internal.server.jgroups;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerFactory;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * JGroups Manager Application.
 */
public class JGroupsManager implements IApplication {

	private IContainer managerContainer = null;
	private ServiceTracker containerManagerTracker;
	private boolean done = false;
	private Object appLock = new Object();

	private String[] mungeArguments(String originalArgs[]) {
		if (originalArgs == null)
			return new String[0];
		final List<String> l = new ArrayList<String>();
		for (int i = 0; i < originalArgs.length; i++)
			if (!originalArgs[i].equals("-pdelaunch")) //$NON-NLS-1$
				l.add(originalArgs[i]);
		return l.toArray(new String[] {});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.
	 * IApplicationContext)
	 */
	public Object start(IApplicationContext context) throws Exception {
		final String[] args = mungeArguments((String[]) context.getArguments()
				.get("application.args")); //$NON-NLS-1$
		if (args.length < 1) {
			usage();
			return IApplication.EXIT_OK;
		} else {
			managerContainer = createContainer("ecf.jgroups.manager", args[0]);
			System.out.println("JGroups Manager started with id="
					+ managerContainer.getID());
			waitForDone();
			return IApplication.EXIT_OK;
		}

	}

	protected void waitForDone() {
		// then just wait here
		synchronized (appLock) {
			while (!done) {
				try {
					appLock.wait();
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	public void stop() {
		if (managerContainer != null) {
			managerContainer.dispose();
			getContainerManager().removeAllContainers();
			managerContainer = null;
		}
		if (containerManagerTracker != null) {
			containerManagerTracker.close();
			containerManagerTracker = null;
		}
		synchronized (appLock) {
			done = true;
			appLock.notifyAll();
		}
	}

	protected IContainerManager getContainerManager() {
		if (containerManagerTracker == null) {
			containerManagerTracker = new ServiceTracker(
					Activator.getContext(), IContainerManager.class.getName(),
					null);
			containerManagerTracker.open();
		}
		return (IContainerManager) containerManagerTracker.getService();
	}

	protected IContainer createContainer(String containerType,
			String containerId) throws ContainerCreateException {
		IContainerFactory containerFactory = getContainerManager()
				.getContainerFactory();
		return (containerId == null) ? containerFactory
				.createContainer(containerType) : containerFactory
				.createContainer(containerType, new Object[] { containerId });
	}

	private void usage() {
		System.out.println("Usage: eclipse.exe -application " //$NON-NLS-1$
				+ this.getClass().getName()
				+ " jgroups:///<jgroupsChannelName>"); //$NON-NLS-1$
		System.out
				.println("   Examples: eclipse -application org.eclipse.ecf.provider.jgroups.JGroupsManager jgroups:///jgroupsChannel"); //$NON-NLS-1$
	}

}
