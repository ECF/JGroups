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
package org.eclipse.ecf.internal.provider.jgroups.ui;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

public class JoinGroupWizardAction implements IObjectActionDelegate,
		IWorkbenchWindowActionDelegate {

	private static final String CONNECT_PROJECT_MENU_TEXT = Messages.JoinGroupWizardAction_PROJECT_MENU_CONNECT_TEXT;
	private static final String DISCONNECT_PROJECT_MENU_TEXT = Messages.JoinGroupWizardAction_PROJECT_MENU_DISCONNECT_TEXT;

	private IResource resource;
	private boolean connected = false;
	private IWorkbenchPart targetPart;
	private IWorkbenchWindow window;

	private String connectID = null;
	private IContainer client;
	private ID targetID;
	private String nickName;
	
	public JoinGroupWizardAction() {
		super();
	}
	
	public JoinGroupWizardAction(String connectID) {
		this();
		this.connectID = connectID;
	}
	
	public JoinGroupWizardAction(IContainer container, ID targetID,
			String nickName) {
		this();
		this.client = container;
		this.targetID = targetID;
		this.nickName = nickName;
	}

	private ClientEntry isConnected(IResource res) {
		if (res == null)
			return null;
		CollabClient client = CollabClient.getDefault();
		ClientEntry entry = client.isConnected(res,
				CollabClient.JGROUPS_CONTAINER_CLIENT_NAME);
		return entry;
	}

	private void setAction(IAction action, IResource resource) {
		if (isConnected(resource) != null) {
			action.setText(DISCONNECT_PROJECT_MENU_TEXT);
			connected = true;
		} else {
			action.setText(CONNECT_PROJECT_MENU_TEXT);
			connected = false;
		}
		action.setEnabled(resource == null ? false : resource.isAccessible());
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		this.targetPart = targetPart;
	}

	public void run(IAction action) {
		if (!connected) {
			JoinGroupWizard wizard = new JoinGroupWizard(resource, PlatformUI.getWorkbench(), connectID);
			Shell shell = null;
			if (targetPart == null) {
				shell = (window == null)?null:window.getShell();
			} else {
				shell = targetPart.getSite().getShell();
			}
			// Create the wizard dialog
			WizardDialog dialog = new WizardDialog(shell, wizard);
			// Open the wizard dialog
			dialog.open();
		} else {
			ClientEntry client = isConnected(resource);
			if (client == null) {
				connected = false;
				action.setText(CONNECT_PROJECT_MENU_TEXT);
			} else {
			}
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
}
