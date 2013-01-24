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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerListener;
import org.eclipse.ecf.core.events.IContainerConnectedEvent;
import org.eclipse.ecf.core.events.IContainerEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.internal.provider.jgroups.ui.Activator;
import org.eclipse.ecf.presence.IIMMessageEvent;
import org.eclipse.ecf.presence.IIMMessageListener;
import org.eclipse.ecf.presence.IPresenceContainerAdapter;
import org.eclipse.ecf.presence.im.IChatManager;
import org.eclipse.ecf.presence.im.IChatMessage;
import org.eclipse.ecf.presence.im.IChatMessageEvent;
import org.eclipse.ecf.presence.im.IChatMessageSender;
import org.eclipse.ecf.presence.im.ITypingMessageEvent;
import org.eclipse.ecf.presence.im.ITypingMessageSender;
import org.eclipse.ecf.presence.ui.MessagesView;
import org.eclipse.ecf.presence.ui.MultiRosterView;
import org.eclipse.ecf.ui.IConnectWizard;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

public class JoinGroupWizard extends Wizard implements IConnectWizard,
		INewWizard {

	private static final String DIALOG_SETTINGS = JoinGroupWizard.class
			.getName();

	JoinGroupWizardPage mainPage;
	private IResource resource;

	protected IContainer container;

	private IWorkbench workbench;

	private IChatMessageSender icms;

	private ITypingMessageSender itms;

	private ID targetID;

	public JoinGroupWizard() {
	}

	public JoinGroupWizard(IResource resource, IWorkbench workbench) {
		super();
		this.resource = resource;
		setWindowTitle("JavaGroups Connect");
		final IDialogSettings dialogSettings = Activator.getDefault()
				.getDialogSettings();
		IDialogSettings wizardSettings = dialogSettings
				.getSection(DIALOG_SETTINGS);
		if (wizardSettings == null)
			wizardSettings = dialogSettings.addNewSection(DIALOG_SETTINGS);

		setDialogSettings(wizardSettings);
	}

	protected ISchedulingRule getSchedulingRule() {
		return resource;
	}

	public void addPages() {
		super.addPages();
		mainPage = new JoinGroupWizardPage();
		addPage(mainPage);
	}

	public boolean performFinish() {
		try {
			finishPage(new NullProgressMonitor());
		} catch (final Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	protected void finishPage(final IProgressMonitor monitor)
			throws InterruptedException, CoreException {

		mainPage.saveDialogSettings();
		JoinGroupWizardAction client = null;
		final String targetURI = mainPage.getJoinGroupText();
		final String connectID = mainPage.getNicknameText();
		@SuppressWarnings("unused")
		final String containerType = JGroups.CLIENT_CONTAINER_NAME; // TODO [pierre] change to extension point value
		
		try {
			targetID = container.getConnectNamespace().createInstance(
					new Object[] { targetURI });


			container.addListener(new IContainerListener() {
				public void handleEvent(IContainerEvent event) {
					if (event instanceof IContainerConnectedEvent) {
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								openView();
							}
						});
					}
				}
			});


			client = new JoinGroupWizardAction(container, targetID, connectID);
			
			client.run(null);
		} catch (final Exception e) {
			final String id = Activator.getDefault().getBundle()
					.getSymbolicName();
			throw new CoreException(new Status(Status.ERROR, id, IStatus.ERROR,
					"Could not connect to " + targetURI, e));
		}

		final IPresenceContainerAdapter adapter = (IPresenceContainerAdapter) container
				.getAdapter(IPresenceContainerAdapter.class);

		final IChatManager icm = adapter.getChatManager();
		icms = icm.getChatMessageSender();
		itms = icm.getTypingMessageSender();

		icm.addMessageListener(new IIMMessageListener() {
			public void handleMessageEvent(IIMMessageEvent e) {
				if (e instanceof IChatMessageEvent) {
					displayMessage((IChatMessageEvent) e);
				} else if (e instanceof ITypingMessageEvent) {
					displayTypingNotification((ITypingMessageEvent) e);
				}
			}
		});

	}

	public void init(IWorkbench workbench, IContainer container) {
		this.workbench = workbench;
		this.container = container;

		setWindowTitle(JoinGroupWizard.DIALOG_SETTINGS);
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.container = null;
		try {
			this.container = ContainerFactory.getDefault().createContainer(
					JGroups.CLIENT_CONTAINER_NAME);
		} catch (final ContainerCreateException e) {
			// None
		}

		setWindowTitle(JoinGroupWizard.DIALOG_SETTINGS);

	}

	private void openView() {
		try {
			MultiRosterView view = (MultiRosterView) workbench
					.getActiveWorkbenchWindow().getActivePage()
					.findView(MultiRosterView.VIEW_ID);
			if (view == null) {
				view = (MultiRosterView) workbench
						.getActiveWorkbenchWindow()
						.getActivePage()
						.showView(MultiRosterView.VIEW_ID, null,
								IWorkbenchPage.VIEW_CREATE);
			}
			view.addContainer(container);
			final IWorkbenchPage page = workbench.getActiveWorkbenchWindow()
					.getActivePage();
			if (!page.isPartVisible(view)) {
				final IWorkbenchSiteProgressService service = (IWorkbenchSiteProgressService) view
						.getSite().getAdapter(
								IWorkbenchSiteProgressService.class);
				service.warnOfContentChange();
			}
		} catch (final PartInitException e) {
			e.printStackTrace();
		}
	}

	private void displayMessage(IChatMessageEvent e) {
		final IChatMessage message = e.getChatMessage();
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				MessagesView view = (MessagesView) workbench
						.getActiveWorkbenchWindow().getActivePage()
						.findView(MessagesView.VIEW_ID);
				if (view != null) {
					final IWorkbenchSiteProgressService service = (IWorkbenchSiteProgressService) view
							.getSite().getAdapter(
									IWorkbenchSiteProgressService.class);
					view.openTab(icms, itms, targetID, message.getFromID());
					view.showMessage(message);
					service.warnOfContentChange();
				} else {
					try {
						final IWorkbenchPage page = workbench
								.getActiveWorkbenchWindow().getActivePage();
						view = (MessagesView) page.showView(
								MessagesView.VIEW_ID, null,
								IWorkbenchPage.VIEW_CREATE);
						if (!page.isPartVisible(view)) {
							final IWorkbenchSiteProgressService service = (IWorkbenchSiteProgressService) view
									.getSite()
									.getAdapter(
											IWorkbenchSiteProgressService.class);
							service.warnOfContentChange();
						}
						view.openTab(icms, itms, targetID, message.getFromID());
						view.showMessage(message);
					} catch (final PartInitException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	private void displayTypingNotification(final ITypingMessageEvent e) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				final MessagesView view = (MessagesView) workbench
						.getActiveWorkbenchWindow().getActivePage()
						.findView(MessagesView.VIEW_ID);
				if (view != null) {
					view.displayTypingNotification(e);
				}
			}
		});
	}
}
