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

package org.wtlnw.eclipse.log4j.viewer.ui.dialogs;

import java.util.function.Function;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.eclipse.jdt.internal.debug.ui.console.JavaStackTraceConsole;
import org.eclipse.jdt.internal.debug.ui.console.JavaStackTraceConsoleViewer;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.FillLayoutFactory;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.window.WindowManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IPatternMatchListener;
import org.osgi.framework.FrameworkUtil;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventProperty;
import org.wtlnw.eclipse.log4j.viewer.ui.util.Util;

/**
 * A {@link TrayDialog} implementation displaying detail information
 * for a {@link LogEvent} instance.
 */
public class LogEventDetailDialog extends TrayDialog {
	
	/**
	 * The {@link String} to display instead of {@code null values}.
	 */
	private static final String NOT_AVAILABLE = "N/A";

	/**
	 * {@link GridDataFactory} constant for all labels in this dialog.
	 */
	private static final GridDataFactory LABEL_DATA = GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER);

	/**
	 * {@link GridDataFactory} constant for all text fields in this dialog.
	 */
	private static final GridDataFactory TEXT_DATA = GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false);

	/**
	 * The name of the settings section to store settings for this dialog.
	 */
	private static final String DIALOG_SETTINGS = "LogEventDetailDialogSettings";
	
	/**
	 * @see #getEvent()
	 */
	private final LogEvent _event;

	/**
	 * Create a {@link LogEventDetailDialog} displaying the given {@link LogEvent}.
	 * 
	 * @param shell see {@link #getShell()}
	 * @param event see {@link #getEvent()}
	 */
	public LogEventDetailDialog(final Shell shell, final LogEvent event) {
		super(shell);

		// make sure to use non-modal dialogs with a shell trim.
		setShellStyle(SWT.SHELL_TRIM);

		_event = event;
	}

	/**
	 * @return the {@link LogEvent} to be displayed
	 */
	public LogEvent getEvent() {
		return _event;
	}

	@Override
	public boolean isHelpAvailable() {
		return false;
	}
	
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		final IDialogSettings settings = PlatformUI.getDialogSettingsProvider(FrameworkUtil.getBundle(LogEventDetailDialog.class)).getDialogSettings();
		final IDialogSettings section = settings.getSection(DIALOG_SETTINGS);
		if (section != null) {
			return section;
		}
		return settings.addNewSection(DIALOG_SETTINGS);
	}
	
	@Override
	protected int getDialogBoundsStrategy() {
		// do NOT persist dialog location, size only
		return DIALOG_PERSISTSIZE;
	}

	@Override
	protected void configureShell(final Shell shell) {
		super.configureShell(shell);

		// set dialog's title text dynamically
		shell.setText(String.format("Event Details for %s @ %s", LogEventProperty.LEVEL.getValueProvider().apply(_event), LogEventProperty.TIMESTAMP.getValueProvider().apply(_event)));
	}

	@Override
	protected Point getInitialLocation(final Point initialSize) {
		// compute the initial position based on the previous detail dialog
		final WindowManager mgr = getWindowManager();
		if (mgr != null) {
			final Window[] windows = mgr.getWindows();
			for (int i = windows.length - 1; i >= 0; i--) {
				final Window window = windows[i];
				if (window != this) {
					final int offset = 32;
					final Rectangle prevBounds = window.getShell().getBounds();
					
					return new Point(
							prevBounds.x + offset,
							prevBounds.y + offset);
				}
			}
		}

		// use default implementation if there is none
		return super.getInitialLocation(initialSize);
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.CLOSE_LABEL, true);
	}
	
	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite contents = (Composite) super.createDialogArea(parent);
		FillLayoutFactory.fillDefaults()
			.margins(IDialogConstants.HORIZONTAL_MARGIN, IDialogConstants.VERTICAL_MARGIN)
			.applyTo(contents);

		final SashForm sash = new SashForm(contents, SWT.VERTICAL);
		createGeneralFields(sash);

		// stack trace
		final String throwable = LogEventProperty.THROWABLE.getValueProvider().apply(_event);
		if (!throwable.isEmpty()) {
			createStackTrace(sash);
		}
		
		return contents;
	}

	private void createStackTrace(final SashForm sash) {
		final Group traceGroup = new Group(sash, SWT.SHADOW_NONE);
		traceGroup.setText(LogEventProperty.THROWABLE.getName() + ":");
		FillLayoutFactory.fillDefaults().margins(3, 3).applyTo(traceGroup);

		@SuppressWarnings("deprecation")
		final ThrowableProxy thrown = _event.getThrownProxy();
		@SuppressWarnings("deprecation")
		final String stacktrace = thrown.getExtendedStackTraceAsString();

		// create a new console and initialize pattern match listeners for it
		final JavaStackTraceConsole console = new JavaStackTraceConsole();
		final IPatternMatchListener[] listeners = ConsolePlugin.getDefault().getConsoleManager().createPatternMatchListeners(console);
		for (final IPatternMatchListener listener : listeners) {
			console.addPatternMatchListener(listener);
		}

		// set the console input AFTER initializing pattern match listeners
		console.getDocument().set(stacktrace);

		// now create the viewer and add it to the trace group
		final JavaStackTraceConsoleViewer viewer = new JavaStackTraceConsoleViewer(traceGroup, console);
		viewer.setEditable(false);
		// set the group's background color for seamless integration
		viewer.getControl().setBackground(traceGroup.getBackground());

		// make sure to destroy the console when the dialog is closed
		traceGroup.addDisposeListener(e -> {
			// disconnect the listeners first
			for (final IPatternMatchListener listener : listeners) {
				console.removePatternMatchListener(listener);
			}

			// destroy the console instance itself
			console.destroy();
		});
	}

	private Composite createGeneralFields(final SashForm parent) {
		final Composite general = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().margins(0, 0).numColumns(2).applyTo(general);
		
		createField(general, LogEventProperty.CATEGORY.getName() + ": ", LogEventProperty.CATEGORY.getValueProvider());
		createField(general, LogEventProperty.LEVEL.getName() + ": ", LogEventProperty.LEVEL.getValueProvider());
		createField(general, LogEventProperty.TIMESTAMP.getName() + ":", LogEventProperty.TIMESTAMP.getValueProvider());
		
		final Group msgGroup = new Group(general, SWT.SHADOW_NONE);
		msgGroup.setText(LogEventProperty.MESSAGE.getName() + ":");
		GridDataFactory.fillDefaults().span(2, 1).align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(msgGroup);
		FillLayoutFactory.fillDefaults().margins(3, 3).applyTo(msgGroup);
		
		final Text msgText = new Text(msgGroup, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
		msgText.setEditable(false);
		msgText.setText(LogEventProperty.MESSAGE.getValueProvider().apply(_event));
		// set the group's background color for seamless integration
		msgText.setBackground(msgGroup.getBackground());
		
		return general;
	}

	private void createField(final Composite contents, final String label, final Function<LogEvent, String> value) {
		final Label labelField = new Label(contents, SWT.NONE);
		labelField.setText(label);
		LABEL_DATA.applyTo(labelField);

		final Label textField = new Label(contents, SWT.NONE);
		textField.setText(Util.nonNullOrElse(value.apply(_event), NOT_AVAILABLE));
		TEXT_DATA.applyTo(textField);
	}
}
