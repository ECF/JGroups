/****************************************************************************
 * Copyright (c) 2004, 2007 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups.ui.wizard;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public class JoinGroupWizardAction implements IObjectActionDelegate,
		IWorkbenchWindowActionDelegate {

	private IResource resource;
	private boolean connected = false;
	private IWorkbenchPart targetPart;
	private IWorkbenchWindow window;

	private ID targetID = null;
	private String nickName;
	private IContainer client;

	public JoinGroupWizardAction() {
		super();
	}

	public JoinGroupWizardAction(IContainer container, ID targetID,
			String nickName) {
		this();
		this.client = container;
		this.targetID = targetID;
		this.nickName = nickName;
	}

	private void setAction(IAction action, IResource resource) {
		action.setEnabled(resource == null ? false : resource.isAccessible());
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		this.targetPart = targetPart;
	}

	public void run(IAction action) {

		try {

			client.connect(targetID, null);

		} catch (ContainerConnectException e) {
			e.printStackTrace();
		}

	}

	public void selectionChanged(IAction action, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection iss = (IStructuredSelection) selection;
			Object obj = iss.getFirstElement();
			if (obj instanceof IProject) {
				resource = (IProject) obj;
			} else if (obj instanceof IAdaptable) {
				resource = (IProject) ((IAdaptable) obj)
						.getAdapter(IProject.class);
			} else {
				resource = ResourcesPlugin.getWorkspace().getRoot();
			}
		} else {
			resource = ResourcesPlugin.getWorkspace().getRoot();
		}
		setAction(action, resource);
	}

	public void dispose() {
		// TODO Auto-generated method stub

	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	protected String getClientContainerName() {
		return JGroups.CLIENT_CONTAINER_NAME;
	}
}
