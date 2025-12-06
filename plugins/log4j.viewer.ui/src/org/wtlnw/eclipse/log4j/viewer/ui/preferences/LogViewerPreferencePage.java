/********************************************************************************
 * Copyright (c) 2025 wtlnw and contributors
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache Software License 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 ********************************************************************************/

package org.wtlnw.eclipse.log4j.viewer.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;
import org.wtlnw.eclipse.log4j.viewer.ui.views.LogViewerPart;

/**
 * {@link IWorkbenchPreferencePage} implementation for {@link LogViewerPart} settings.
 */
public class LogViewerPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	/**
	 * Create a {@link LogViewerPreferencePage}.
	 */
	public LogViewerPreferencePage() {
		super(GRID);
		setDescription("In order for the changes to take effect, the viewer has to be re-opened.");
	}

	@Override
	protected IPreferenceStore doGetPreferenceStore() {
		return Activator.getInstance().getPreferenceStore();
	}
	
	@Override
	public void createFieldEditors() {
		addField(new IntegerFieldEditor(LogViewerPreferenceConstants.PORT, "Server &port: ", getFieldEditorParent()));
		addField(new IntegerFieldEditor(LogViewerPreferenceConstants.TIMEOUT, "Server &timeout [ms]: ", getFieldEditorParent()));
		addField(new BooleanFieldEditor(LogViewerPreferenceConstants.AUTOSTART, "Server &autostart", getFieldEditorParent()));
		addField(new IntegerFieldEditor(LogViewerPreferenceConstants.BUFFER, "Event &buffer: ", getFieldEditorParent()));
		addField(new IntegerFieldEditor(LogViewerPreferenceConstants.REFRESH, "&UI refresh interval [ms]: ", getFieldEditorParent()));
		
		addField(new ColorFieldEditor(LogViewerPreferenceConstants.COLOR_DEBUG, "&Debug color: ", getFieldEditorParent()));
		addField(new ColorFieldEditor(LogViewerPreferenceConstants.COLOR_INFO, "&Info color: ", getFieldEditorParent()));
		addField(new ColorFieldEditor(LogViewerPreferenceConstants.COLOR_WARN, "&Warning color: ", getFieldEditorParent()));
		addField(new ColorFieldEditor(LogViewerPreferenceConstants.COLOR_ERROR, "&Error color: ", getFieldEditorParent()));
		addField(new ColorFieldEditor(LogViewerPreferenceConstants.COLOR_FATAL, "&Fatal color: ", getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing to do
	}
}