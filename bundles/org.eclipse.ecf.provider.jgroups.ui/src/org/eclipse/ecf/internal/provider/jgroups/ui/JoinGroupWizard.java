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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.presence.chatroom.IChatRoomManager;
import org.eclipse.ecf.provider.jgroups.identity.JGroupsNamespace;
import org.eclipse.ecf.ui.IConnectWizard;
import org.eclipse.ecf.ui.util.PasswordCacheHelper;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

public class JoinGroupWizard extends Wizard implements IConnectWizard,
		INewWizard {

	public JoinGroupWizard() {
		super();
	}

	private static final String DIALOG_SETTINGS = JoinGroupWizard.class
			.getName();

	private static final String GROUP_PROTOCOL = "JGroups";

	JoinGroupWizardPage mainPage;
	private IResource resource;

	private String connectID;

	private IContainer container;

	public JoinGroupWizard(IResource resource, IWorkbench workbench) {
		super();
		this.resource = resource;
		setWindowTitle(Messages.JoinGroupWizardPage_CONNECT_JGROUPS_TITLE);
		final IDialogSettings dialogSettings = ClientPlugin.getDefault()
				.getDialogSettings();
		IDialogSettings wizardSettings = dialogSettings
				.getSection(DIALOG_SETTINGS);
		if (wizardSettings == null)
			wizardSettings = dialogSettings.addNewSection(DIALOG_SETTINGS);

		setDialogSettings(wizardSettings);
	}

	public JoinGroupWizard(IResource resource, IWorkbench workbench,
			String connectID) {
		this(resource, workbench);
		this.connectID = connectID;
	}

	protected ISchedulingRule getSchedulingRule() {
		return resource;
	}

	public void addPages() {
		mainPage = new JoinGroupWizardPage(connectID);
		addPage(mainPage);
	}

	public boolean performCancel() {
		container.dispose();

		IContainerManager containerManager = Activator.getDefault()
				.getContainerManager();
		if (containerManager != null) {
			containerManager.removeContainer(container);
		}

		return super.performCancel();
	}

	public boolean performFinish() {

		mainPage.saveDialogSettings();
		URIClientConnectAction client = null;
		final String groupName = mainPage.getJoinGroupText();
		final String nickName = mainPage.getNicknameText();
		final String containerType = mainPage.getContainerType();
		final boolean autoLogin = mainPage.getAutoLoginFlag();
		client = new URIClientConnectAction(containerType, groupName, nickName,
				"", resource, autoLogin); //$NON-NLS-1$
		client.run(null);

		final IChatRoomManager manager = (IChatRoomManager) this.container
				.getAdapter(IChatRoomManager.class);
		final JGroupsUI ui = new JGroupsUI(this.container, manager, null);
		ui.showForTarget(IDFactory.getDefault().createID(JGroupsNamespace.NAME, groupName));
		return true;
	}

	protected void cachePassword(final String connectID, String password) {
		if (password != null && !password.equals("")) {
			final PasswordCacheHelper pwStorage = new PasswordCacheHelper(
					connectID);
			pwStorage.savePassword(password);
		}
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.container = null;
		try {
			this.container = ContainerFactory.getDefault().createContainer(
					"ecf.jgroups.client");
		} catch (final ContainerCreateException e) {
			// None
		}

		setWindowTitle(Messages.JoinGroupWizardPage_CONNECT_JGROUPS_TITLE);

	}

	public void init(IWorkbench workbench, IContainer container) {
		this.container = container;

		setWindowTitle(Messages.JoinGroupWizardPage_PROTOCOL + " "
				+ GROUP_PROTOCOL);
	}
}
