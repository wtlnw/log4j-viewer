package org.wtlnw.eclipse.log4j.viewer.ui.dialogs;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventPropertyFilter;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;

/**
 * A {@link PopupDialog} providing a form for {@link LogEventPropertyFilter}s.
 */
public class LogEventColumnFilterPopup extends PopupDialog {
	
	/**
	 * Return code constant (value 3) indicating that the filter was cleared.
	 */
	public static final int CLEAR = 3;
	
	/**
	 * @see #getItem()
	 */
	private final ToolItem _item;

	/**
	 * @see #getFilter()
	 */
	private final LogEventPropertyFilter _filter;

	/**
	 * The {@link Text} field for filter pattern input, only needed for {@link #getFocusControl()}.
	 */
	private Text _input;

	/**
	 * Create a {@link LogEventColumnFilterPopup}.
	 * 
	 * @param item   see {@link #getItem()}
	 * @param filter see {@link #getFilter()}
	 */
	public LogEventColumnFilterPopup(final ToolItem item, final LogEventPropertyFilter filter) {
		super(item.getParent().getShell(), SWT.NO_TRIM, true, false, false, false, false, false, null, null);

		_item = item;
		_filter = filter;

		// IMPORTANT: we want the dialog to return OK only if the APPLY button
		// was pressed, otherwise CANCEL must be returned.
		setReturnCode(CANCEL);
	}

	/**
	 * @return the {@link ToolItem} to display the dialog for
	 */
	public ToolItem getItem() {
		return _item;
	}
	
	/**
	 * @return the {@link LogEventPropertyFilter} to display and modify the contents
	 *         of or {@code null} if no filtering is desired
	 */
	public LogEventPropertyFilter getFilter() {
		return _filter;
	}
	
	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite contents = (Composite) super.createDialogArea(parent);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(contents);
		
		final Label label = new Label(contents, SWT.NONE);
		label.setText("Filter " + getFilter().getProperty().getName());
		label.setFont(JFaceResources.getBannerFont());
		label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		final ToolBar toolbar = new ToolBar(contents, SWT.FLAT);
		toolbar.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		
		final ToolItem matchCase = new ToolItem(toolbar, SWT.CHECK);
		matchCase.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_MATCH_CASE));
		matchCase.setToolTipText("Match case");
		matchCase.setSelection(_filter.isMatchCase());
		
		final ToolItem regex = new ToolItem(toolbar, SWT.CHECK);
		regex.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_REGEX));
		regex.setToolTipText("Match regular expression pattern");
		regex.setSelection(_filter.isRegularExpression());

		final ToolItem wholeWord = new ToolItem(toolbar, SWT.CHECK);
		wholeWord.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_WHOLE_WORD));
		wholeWord.setToolTipText("Match whole word");
		wholeWord.setSelection(_filter.isWholeWord());
		
		final ToolItem inverse = new ToolItem(toolbar, SWT.CHECK);
		inverse.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_INVERT));
		inverse.setToolTipText("Invert match result");
		inverse.setSelection(_filter.isInverse());
		
		_input = new Text(contents, SWT.BORDER);
		_input.setMessage("Enter filter text");
		_input.setText(_filter.getPattern());
		_input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		final Composite buttons = new Composite(contents, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false, 2, 1));
		final GridLayout buttonsLayout = new GridLayout(3, true);
		buttonsLayout.marginWidth = 0;
		buttons.setLayout(buttonsLayout);
		
		final Button clear = new Button(buttons, SWT.PUSH);
		clear.setText("Clear");
		clear.setToolTipText("Clear the filter and close the popup.");
		clear.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		clear.addSelectionListener(widgetSelectedAdapter(e -> {
			setReturnCode(CLEAR);
			close();
		}));
		
		final Button cancel = new Button(buttons, SWT.PUSH);
		cancel.setText("Cancel");
		cancel.setToolTipText("Dismiss changes and close the popup.");
		cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		cancel.addSelectionListener(widgetSelectedAdapter(e -> {
			setReturnCode(CANCEL);
			close();
		}));
		
		final Button apply = new Button(buttons, SWT.PUSH);
		apply.setText("Apply");
		apply.setToolTipText("Apply changes and close the popup.");
		apply.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		apply.addSelectionListener(widgetSelectedAdapter(se -> {
			_filter.setInverse(inverse.getSelection());
			_filter.setMatchCase(matchCase.getSelection());
			_filter.setWholeWord(wholeWord.getSelection());
			_filter.setRegularExpression(regex.getSelection());
			_filter.setPattern(_input.getText());
			setReturnCode(OK);
			close();
		}));
		
		// now add listeners to controls which require re-validation of input upon changes
		regex.addSelectionListener(widgetSelectedAdapter(e -> validate(regex, _input, apply)));
		_input.addModifyListener(e -> validate(regex, _input, apply));

		// register apply button as default and set focus to input field
		getShell().setDefaultButton(apply);
		
		return contents;
	}

	@Override
	protected Point getDefaultSize() {
		final Point size = super.getDefaultSize();

		// expand the width of the dialog to avoid hard-coded
		// pixel values when creating controls. 
		size.x *= 1.5;
		
		return size;
	}
	
	@Override
	protected Control getFocusControl() {
		return _input;
	}
	
	@Override
	protected void adjustBounds() {
		final Rectangle itemBounds = _item.getBounds();
		getShell().setLocation(_item.getParent().toDisplay(new Point(itemBounds.x, itemBounds.y + itemBounds.height)));
	}
	
	private void validate(final ToolItem regex, final Text input, final Button apply) {
		if (regex.getSelection()) {
			try {
				Pattern.compile(input.getText());
			} catch (PatternSyntaxException ex) {
				input.setForeground(input.getDisplay().getSystemColor(SWT.COLOR_RED));
				input.setToolTipText(ex.getMessage());
				apply.setEnabled(false);
				return;
			}	
		}

		input.setForeground(null);
		input.setToolTipText(null);
		apply.setEnabled(true);
	}
}
