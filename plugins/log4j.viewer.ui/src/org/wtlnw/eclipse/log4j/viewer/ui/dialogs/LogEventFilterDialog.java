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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventFilter;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventProperty;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventPropertyFilter;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;

/**
 * A {@link TrayDialog} specialization allowing users to activate/deactivate
 * and configure column filters for the log event table.
 */
public class LogEventFilterDialog extends TrayDialog {

	/**
	 * @see #getFilter()
	 */
	private final LogEventFilter _filter;

	/**
	 * A {@link Set} of invalid {@link LogEventProperty} filter configurations. 
	 */
	private final Set<LogEventProperty> _errors = new HashSet<>();

	/**
	 * A {@link Set} of {@link Runnable}s which are called when the OK button was
	 * pressed in order to transfer the user input into the appropriate filter.
	 */
	private final Set<Runnable> _appliers = new HashSet<>();
	
	/**
	 * Create a {@link LogEventFilterDialog}.
	 * 
	 * @param parent see {@link #getParentShell()}
	 * @param filter the {@link LogEventFilter} to modify a copy of
	 */
	public LogEventFilterDialog(final Shell parent, final LogEventFilter filter) {
		super(parent);

		// make sure to use non-modal dialogs with a shell trim.
		setShellStyle(SWT.SHELL_TRIM);

		_filter = new LogEventFilter();

		// copy the contents of the given filter
		for (final LogEventPropertyFilter src : filter.getFilters()) {
			LogEventPropertyFilter.copy(src, _filter.get(src.getProperty()));
		}
	}

	/**
	 * @return the (possibly) modified copy of the {@link LogEventFilter} the
	 *         receiver was created with
	 */
	public LogEventFilter getFilter() {
		return _filter;
	}

	@Override
	protected void configureShell(final Shell newShell) {
		super.configureShell(newShell);

		newShell.setText("Log Viewer Filter");
	}

	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite contents = (Composite) super.createDialogArea(parent);

		final GridDataFactory ldf = GridDataFactory.swtDefaults()
				.minSize(convertWidthInCharsToPixels(48), SWT.DEFAULT)
				.align(SWT.FILL, SWT.CENTER)
				.grab(true, false);

		for (final LogEventPropertyFilter filter : _filter.getFilters()) {
			final Control control = createFilterControl(contents, filter);
			ldf.applyTo(control);
		}

		return contents;
	}

	/**
	 * Create the {@link Control} displaying the given
	 * {@link LogEventPropertyFilter}.
	 * 
	 * @param parent the {@link Composite} to create the {@link Control} in
	 * @param filter the {@link LogEventPropertyFilter} to create the
	 *               {@link Control} for
	 * @return the new {@link Control}
	 */
	private Control createFilterControl(final Composite parent, final LogEventPropertyFilter filter) {
		final Group group = new Group(parent, SWT.NONE);
		group.setText(filter.getProperty().getName());
		GridLayoutFactory.swtDefaults().applyTo(group);
		
		final ToolBar toolbar = new ToolBar(group, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

		final ToolItem enabled = new ToolItem(toolbar, SWT.CHECK);
		enabled.setImage(getEnabledImageFunction().apply(filter.isEnabled()));
		enabled.setToolTipText("Activate to enable");
		enabled.setSelection(filter.isEnabled());
		enabled.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			enabled.setImage(getEnabledImageFunction().apply(enabled.getSelection()));
		}));
		
		new ToolItem(toolbar, SWT.SEPARATOR);
		
		final ToolItem matchCase = new ToolItem(toolbar, SWT.CHECK);
		matchCase.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_MATCH_CASE));
		matchCase.setToolTipText("Match case");
		matchCase.setSelection(filter.isMatchCase());
		
		final ToolItem regex = new ToolItem(toolbar, SWT.CHECK);
		regex.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_REGEX));
		regex.setToolTipText("Match regular expression pattern");
		regex.setSelection(filter.isRegularExpression());

		final ToolItem wholeWord = new ToolItem(toolbar, SWT.CHECK);
		wholeWord.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_WHOLE_WORD));
		wholeWord.setToolTipText("Match whole word");
		wholeWord.setSelection(filter.isWholeWord());

		new ToolItem(toolbar, SWT.SEPARATOR);

		final ToolItem inverse = new ToolItem(toolbar, SWT.CHECK);
		inverse.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_INVERT));
		inverse.setToolTipText("Invert match result");
		inverse.setSelection(filter.isInverse());
		
		final Text input = new Text(group, SWT.BORDER);
		input.setMessage("Enter filter text");
		input.setText(filter.getPattern());
		input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		// now add listeners to controls which require re-validation of input upon changes
		regex.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> validate(filter, regex, input)));
		input.addModifyListener(e -> validate(filter, regex, input));

		// register the appropriate apply handler
		_appliers.add(() -> {
			filter.setEnabled(enabled.getSelection());
			filter.setInverse(inverse.getSelection());
			filter.setMatchCase(matchCase.getSelection());
			filter.setPattern(input.getText());
			filter.setRegularExpression(regex.getSelection());
			filter.setWholeWord(wholeWord.getSelection());
		});

		return group;
	}

	/**
	 * Validate the input in the given {@link Control}s for the given
	 * {@link LogEventPropertyFilter}, display hints if input was invalid and update
	 * the executability state of the OK button.
	 * 
	 * @param filter the {@link LogEventPropertyFilter} to validate input for
	 * @param regex  the {@link ToolItem} toggling regular expression on/off
	 * @param input  the {@link Text} containing the provided pattern
	 */
	private void validate(final LogEventPropertyFilter filter, final ToolItem regex, final Text input) {
		Color color = null;
		String tooltip = null;
		Consumer<LogEventProperty> action = _errors::remove;

		if (regex.getSelection()) {
			try {
				Pattern.compile(input.getText());
			} catch (PatternSyntaxException ex) {
				color = input.getDisplay().getSystemColor(SWT.COLOR_RED);
				tooltip = ex.getMessage();
				action = _errors::add;
			}	
		}

		// always update UI 
		input.setForeground(color);
		input.setToolTipText(tooltip);
		action.accept(filter.getProperty());

		// make sure to update the executability of the OK button
		getButton(IDialogConstants.OK_ID).setEnabled(_errors.isEmpty());
	}

	/**
	 * @return the {@link Function} which computes the {@link Image} to be used for
	 *         the {@link ToolItem} representing activation state of a filter
	 */
	private Function<Boolean, Image> getEnabledImageFunction() {
		return enabled -> Activator.getInstance().getImageRegistry()
				.get(enabled ? Activator.IMG_PAUSE : Activator.IMG_START);
	}
	
	@Override
	protected void okPressed() {
		// transfer the values from UI to filters first
		_appliers.forEach(Runnable::run);

		// do, whatever the super-class does last
		super.okPressed();
	}

	@Override
	public boolean isHelpAvailable() {
		return false;
	}
}
