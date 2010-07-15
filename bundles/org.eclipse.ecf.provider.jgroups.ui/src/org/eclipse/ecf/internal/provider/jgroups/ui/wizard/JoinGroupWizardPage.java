/*******************************************************************************
 * Copyright (c) 2004, 2007 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.provider.jgroups.ui.wizard;

import org.eclipse.ecf.ui.SharedImages;
import org.eclipse.ecf.ui.wizards.AbstractConnectWizardPage;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class JoinGroupWizardPage extends AbstractConnectWizardPage {

	protected static final String CLASSNAME = JoinGroupWizardPage.class
			.getName();

	protected static final String USER_NAME_SYSTEM_PROPERTY = "user.name";

	protected static final String PAGE_DESCRIPTION = "Complete account info and choose 'Finish' to login.";
	protected static final String JOINGROUP_FIELDNAME = "JavaGroups Channel URL:";
	protected static final String NICKNAME_FIELDNAME = "Nickname:";
	protected static final String PAGE_TITLE = "Connect JavaGroups Client";

	private static final String DIALOG_SETTINGS = CLASSNAME;

	public JoinGroupWizardPage() {
		super("wizardPage");
		setTitle(PAGE_TITLE);
		setDescription(PAGE_DESCRIPTION);
		setImageDescriptor(SharedImages
				.getImageDescriptor(SharedImages.IMG_COLLABORATION_WIZARD));
	}

	protected String template_url = JGroups.DEFAULT_TARGET_URL_TEMPLATE;
	protected String default_url = JGroups.DEFAULT_TARGET_URL;

	protected Text nicknameText;
	protected Text joinGroupText;
	protected String urlPrefix = "";

	// private Button autoLogin = null;
	private boolean autoLoginFlag = false;

	public boolean getAutoLoginFlag() {
		return autoLoginFlag;
	}

	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		final GridLayout gridLayout = new GridLayout(2, false);
		container.setLayout(gridLayout);
		setControl(container);

		GridData data = new GridData(SWT.FILL, SWT.BEGINNING, true, false);

		Label groupIDLabel = new Label(container, SWT.NONE);
		groupIDLabel.setText(JOINGROUP_FIELDNAME);

		joinGroupText = new Text(container, SWT.BORDER);
		joinGroupText.setText(default_url);
		joinGroupText.setLayoutData(data);

		Label exampleLabel = new Label(container, SWT.NONE);
		exampleLabel.setText(template_url);
		exampleLabel.setLayoutData(new GridData(SWT.END, SWT.BEGINNING, false,
				false, 2, 1));

		joinGroupText.setLayoutData(data);
		joinGroupText.addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				joinGroupText.selectAll();
			}
		});

		Label nicknameLabel = new Label(container, SWT.NONE);
		nicknameLabel.setLayoutData(new GridData());
		nicknameLabel.setText(NICKNAME_FIELDNAME);

		nicknameText = new Text(container, SWT.BORDER);
		nicknameText.setLayoutData(data);
		nicknameText.setText(System.getProperty(USER_NAME_SYSTEM_PROPERTY));
		nicknameText.addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				nicknameText.selectAll();
			}
		});

		addListeners();
		restoreDialogSettings();
	}

	private void addListeners() {
		this.joinGroupText.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				verify(false);
			}
		});
	}

	private void restoreDialogSettings() {
		IDialogSettings dialogSettings = getDialogSettings();
		if (dialogSettings != null) {
			IDialogSettings pageSettings = dialogSettings
					.getSection(DIALOG_SETTINGS);
			if (pageSettings != null) {
				String strVal = pageSettings.get("url");
				if (strVal != null)
					joinGroupText.setText(strVal);

				strVal = pageSettings.get("nickname");
				if (strVal != null)
					nicknameText.setText(strVal);
			}
		}
	}

	public void saveDialogSettings() {
		IDialogSettings dialogSettings = getDialogSettings();
		if (dialogSettings != null) {
			IDialogSettings pageSettings = dialogSettings
					.getSection(DIALOG_SETTINGS);
			if (pageSettings == null)
				pageSettings = dialogSettings.addNewSection(DIALOG_SETTINGS);

			pageSettings.put("url", joinGroupText.getText());
			pageSettings.put("nickname", nicknameText.getText());
		}
	}

	public String getJoinGroupText() {
		String textValue = joinGroupText.getText().trim();
		if (!urlPrefix.equals("") && !textValue.startsWith(urlPrefix)) {
			textValue = urlPrefix + textValue;
		}
		return textValue;
	}

	public String getNicknameText() {
		if (nicknameText == null)
			return null;
		return nicknameText.getText().trim();
	}

	@Override
	public boolean shouldRequestUsername() {
		return false;
	}

	@Override
	public boolean shouldRequestPassword() {
		return false;
	}

	@Override
	public String getExampleID() {
		return null;
	}

	/**
	 * Verifies the user's input to the wizard. Optionally sets the password for
	 * the specified email if one has been stored and is recognized.
	 * 
	 * @param restorePassword
	 *            <tt>true</tt> if the password field should be set if a
	 *            password can be found
	 */
	private void verify(boolean restorePassword) {
		String jgt = joinGroupText.getText().trim();
		if (jgt.equals("")) { //$NON-NLS-1$
			setErrorMessage(Messages.JoinGroupWizardPage_GroupNameRequired);
		} else {
			// TODO [pierre] add a pattern verifier for jggroups uri scheme ?
			setErrorMessage(null);
		}
	}

	public void setErrorMessage(String message) {
		super.setErrorMessage(message);
		setPageComplete(message == null);
	}

}
