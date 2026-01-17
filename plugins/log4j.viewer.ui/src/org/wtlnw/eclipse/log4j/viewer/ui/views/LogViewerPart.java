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

package org.wtlnw.eclipse.log4j.viewer.ui.views;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.window.WindowManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventFilter;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventProperty;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventPropertyFilter;
import org.wtlnw.eclipse.log4j.viewer.core.impl.LogEventServer;
import org.wtlnw.eclipse.log4j.viewer.core.impl.LogEventSupplierRegistry;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;
import org.wtlnw.eclipse.log4j.viewer.ui.dialogs.LogEventDetailDialog;
import org.wtlnw.eclipse.log4j.viewer.ui.dialogs.LogEventFilterDialog;
import org.wtlnw.eclipse.log4j.viewer.ui.preferences.LogViewerPreferenceConstants;

/**
 * A {@link ViewPart} implementation displaying log4j event entries.
 */
public class LogViewerPart extends ViewPart {
	
	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.wtlnw.eclipse.log4j.viewer.ui.views.LogViewerPart";
	
	private IPreferenceStore _prefs;
	private LogEventFilter _filter;
	private ColorRegistry _colors; 
	private Table _table;
	private LogEventServer _server;
	private LogViewerTableModel _model;

	private Action _runAction;
	private Action _pauseAction;
	private Action _clearAction;
	private Action _copyAction;
	private Action _detailsAction;
	private Action _exportAction;
	private Action _filterAction;

	private WindowManager _dialogs;

	private final IPropertyChangeListener _prefListener = e -> {
		if (LogViewerPreferenceConstants.isColor(e.getProperty())) {
			// when preferences are changed by the LogViewerPreferencePage
			// the event seems to have RGB as value. When resetting to default
			// the value is a String... that's a bit odd, but we can handle that.
			final RGB newRgb = switch (e.getNewValue()) {
				case String strRgb -> StringConverter.asRGB(strRgb);
				case RGB rawRgb -> rawRgb;
				default -> null;
			};

			if (newRgb != null) {
				_colors.put(e.getProperty(), newRgb);

				if (_table != null && !_table.isDisposed()) {
					_table.clearAll();
				}
			}
		} else {
			// other properties are not applied automatically
		}
	};

	@Override
	public void saveState(final IMemento memento) {
		super.saveState(memento);
		
		final LogViewerPartMementoHandler handler = new LogViewerPartMementoHandler();
		handler.saveFilter(_filter, memento);
	}

	@Override
	public void init(final IViewSite site, final IMemento memento) throws PartInitException {
		super.init(site, memento);
		
		// load preferences
		_prefs = Activator.getInstance().getPreferenceStore();

		// load and initialize filter
		final LogViewerPartMementoHandler handler = new LogViewerPartMementoHandler();
		_filter = handler.loadFilter(memento);
		
		final int port = _prefs.getInt(LogViewerPreferenceConstants.PORT);
		final int timeout = _prefs.getInt(LogViewerPreferenceConstants.TIMEOUT);
		_server = new LogEventServer(port, timeout, new LogEventSupplierRegistry().getFactories(), e -> {
			// refresh paused, ignore the event
			if (!_pauseAction.isChecked()) {
				_model.put(e);
			}
		});
		_server.addErrorListener((msg, ex) -> {
			final ILog log = Platform.getLog(getClass());

			switch (ex) {
			case EOFException eof:
				log.info(msg);
				break;
			case IllegalStateException ise:
				log.info(msg);
				break;
			default:
				log.error(msg, ex);
				break;
			}
		});
	}
	
	@Override
	public void createPartControl(final Composite parent) {
		// initialize the color registry
		_colors = new ColorRegistry(parent.getDisplay());
		_colors.put(LogViewerPreferenceConstants.COLOR_DEBUG, PreferenceConverter.getColor(_prefs, LogViewerPreferenceConstants.COLOR_DEBUG));
		_colors.put(LogViewerPreferenceConstants.COLOR_INFO, PreferenceConverter.getColor(_prefs, LogViewerPreferenceConstants.COLOR_INFO));
		_colors.put(LogViewerPreferenceConstants.COLOR_WARN, PreferenceConverter.getColor(_prefs, LogViewerPreferenceConstants.COLOR_WARN));
		_colors.put(LogViewerPreferenceConstants.COLOR_ERROR, PreferenceConverter.getColor(_prefs, LogViewerPreferenceConstants.COLOR_ERROR));
		_colors.put(LogViewerPreferenceConstants.COLOR_FATAL, PreferenceConverter.getColor(_prefs, LogViewerPreferenceConstants.COLOR_FATAL));

		// listen to changes of preferred colors and refresh the table
		_prefs.addPropertyChangeListener(_prefListener);
		
		// initialize the detail dialog window manager
		_dialogs = new WindowManager();

		// since our parent will contain only one child - the table
		// we can use TableColumnLayout to distribute the columns
		// over the entire table's width.
		final TableColumnLayout layout = new TableColumnLayout();
		parent.setLayout(layout);

		// initialize the actual event table
		_table = new Table(parent, SWT.VIRTUAL | SWT.FULL_SELECTION);
		_table.setHeaderVisible(true);
		_table.setLinesVisible(true);

		// create TableColumns for each property
		for (final LogEventProperty property : LogEventProperty.values()) {
			final TableColumn column = new TableColumn(_table, SWT.NONE);
			column.setText(property.getName());
			column.setData(property);

			layout.setColumnData(column, new ColumnWeightData(switch (property) {
				case MESSAGE -> 4;
				case CATEGORY -> 2;
				case TIMESTAMP -> 2;
				default -> 1;
			}, true));
		}

		_table.addListener(SWT.SetData, e -> {
			final TableItem item = (TableItem) e.item;

			// Workaround for https://github.com/eclipse-platform/eclipse.platform.swt/issues/139
			// when an event is sent for an item which is not displayed.
			// This happens when a table is cleared while having a selection.
			if (item.isDisposed()) {
				return;
			}
			
			final LogEvent event;
			try {
				event = _model.getEventAt(_table.indexOf(item));
			} catch (final IndexOutOfBoundsException ex) {
				// Workaround for https://github.com/eclipse-platform/eclipse.platform.swt/issues/139
				// when an event is sent for an item which is not displayed.
				// This happens when a table is cleared while having a selection.
				return;
			}

			final Table table = item.getParent();
			final TableColumn[] columns = table.getColumns();
			final String[] text = new String[columns.length];
			for (int i = 0; i < columns.length; i++) {
				// now this might seem weird but multi-line text seems to
				// affect the item's height on GTK by default as of November 2025;
				// we have to cut the text in a way that allows single-line
				// display of TableItems and display the multi-line text
				// as tool-tips.
				final LogEventProperty property = (LogEventProperty) columns[i].getData();
				final String value = property.getValueProvider().apply(event);
				final List<String> lines = value.lines().toList();
				if (lines.size() > 1) {
					text[i] = lines.getFirst();
				} else {
					text[i] = value;
				}
			}
			item.setText(text);

			// apply line color
			item.setForeground(switch (event.getLevel().getStandardLevel()) {
			case DEBUG -> _colors.get(LogViewerPreferenceConstants.COLOR_DEBUG);
			case INFO -> _colors.get(LogViewerPreferenceConstants.COLOR_INFO);
			case WARN -> _colors.get(LogViewerPreferenceConstants.COLOR_WARN);
			case ERROR -> _colors.get(LogViewerPreferenceConstants.COLOR_ERROR);
			case FATAL -> _colors.get(LogViewerPreferenceConstants.COLOR_FATAL);
			default -> null;
			});
		});
		_table.addMouseListener(MouseListener.mouseDoubleClickAdapter(e -> {
			final int index = _table.getSelectionIndex();
			if (index > -1) {
				openDetailDialog(_model.getEventAt(index));
			}
		}));
		
		// initialize the model BEFORE starting the server
		_model = new LogViewerTableModel(_table, _prefs.getInt(LogViewerPreferenceConstants.BUFFER), _filter);
		
		createActions();
		fillContextMenu();
		fillActionBars();

		// start the server AFTER initialization is done
		if (_prefs.getBoolean(LogViewerPreferenceConstants.AUTOSTART)) {
			_server.start();
		}
	}

	/**
	 * Open the {@link LogEventDetailDialog} displaying details for the given
	 * {@link LogEvent}.
	 * 
	 * @param event the {@link LogEvent} to open the detail dialogs for
	 */
	private void openDetailDialog(final LogEvent event) {
		// lookup an already opened dialog and bring it to front
		for (final Window window : _dialogs.getWindows()) {
			if (window instanceof LogEventDetailDialog dialog && dialog.getEvent() == event) {
				dialog.open();
				return;
			}
		}

		// create a new dialog and open it.
		final LogEventDetailDialog dialog = new LogEventDetailDialog(_table.getShell(), event);
		_dialogs.add(dialog);
		dialog.open();
	}

	/**
	 * Add a context menu to the viewer's table.
	 */
	private void fillContextMenu() {
		final MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(mgr -> {
			mgr.add(_copyAction);
			mgr.add(_detailsAction);
		});

		final Menu menu = menuMgr.createContextMenu(_table);
		_table.setMenu(menu);
	}
	
	/**
	 * Add actions to the view's actions bars.
	 */
	private void fillActionBars() {
		final IActionBars bars = getViewSite().getActionBars();

		// fill the pull-down menu (aka burger menu)
		final IMenuManager menu = bars.getMenuManager();
		menu.add(_exportAction);

		// fill the tool bar
		final IToolBarManager toolbar = bars.getToolBarManager();
		toolbar.add(_runAction);
		toolbar.add(new Separator());
		toolbar.add(_pauseAction);
		toolbar.add(_clearAction);
		toolbar.add(_filterAction);
	}

	/**
	 * Create and initialize all actions the receiver or any of its parts require.
	 */
	private void createActions() {
		_runAction = createRunAction();
		_clearAction = createClearAction();
		_pauseAction = createPauseAction();
		_copyAction = createCopyAction();
		_detailsAction = createDetailsAction();
		_exportAction = createExportAction();
		_filterAction = createFilterAction();
	}

	/**
	 * @return a new {@link Action} allowing users to configure log event table's
	 *         column filter
	 */
	private Action createFilterAction() {
		final Function<LogEventFilter, Boolean> activeProvider = filter -> {
			return filter.getFilters().stream().anyMatch(LogEventPropertyFilter::isEnabled);
		};
		
		final Function<LogEventFilter, ImageDescriptor> imageProvider = filter -> {
			final boolean active = activeProvider.apply(filter).booleanValue();
			return Activator.getInstance().getImageRegistry().getDescriptor(active ? Activator.IMG_FILTER_ACTIVE : Activator.IMG_FILTER_INACTIVE);
		};
		
		final Action action = new Action("Filter...", imageProvider.apply(_filter)) {
			@Override
			public void run() {
				final LogEventFilterDialog dialog = new LogEventFilterDialog(getSite().getShell(), _filter);
				dialog.setBlockOnOpen(true);

				if (IDialogConstants.OK_ID == dialog.open()) {
					_filter = dialog.getFilter();
					setImageDescriptor(imageProvider.apply(_filter));
					_model.setFilter(_filter);
				}
			}
		};
		action.setMenuCreator(new IMenuCreator() {
			@Override
			public Menu getMenu(final Control parent) {
				Menu menu = parent.getMenu();
				if (menu == null) {
					menu = new Menu(parent);
				} else {
					// dispose of all items for an existing menu
					for (final MenuItem item : menu.getItems()) {
						item.dispose();
					}
				}
				
				final boolean active = activeProvider.apply(_filter).booleanValue();
				final MenuItem item = new MenuItem(menu, SWT.PUSH);
				item.setEnabled(active);
				item.setText("Clear");
				if (active) {
					item.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
						_filter = new LogEventFilter();
						action.setImageDescriptor(imageProvider.apply(_filter));
						_model.setFilter(_filter);
					}));
				}

				return menu;
			}

			@Override
			public Menu getMenu(final Menu parent) {
				return null;
			}
			
			@Override
			public void dispose() {
				// does nothing
			}
		});
		
		return action;
	}
	
	/**
	 * @return a new {@link Action} allowing users to export the currently displayed
	 *         {@link LogEvent}s to a file
	 */
	private Action createExportAction() {
		final Action action = new Action("Export Displayed Events to File...", PlatformUI.getWorkbench().getSharedImages().getImageDescriptor("IMG_ETOOL_EXPORT_WIZ")) {
			@Override
			public void run() {
				// prompt user for export location
				final FileDialog dialog = new FileDialog(_table.getShell(), SWT.SAVE);
				dialog.setText(getText());
				dialog.setOverwrite(true);
				final String path = dialog.open();

				if (path != null) {
					try (final PrintWriter out = new PrintWriter(path)) {
						final Function<LogEvent, String> serializer = getEventSerializer();
						_model.forEach(e -> out.println(serializer.apply(e)));
					} catch (final FileNotFoundException e) {
						ErrorDialog.openError(_table.getShell(), getText(), null, Status.error("Failed to export displayed events to specified file.", e));
					}
				}
			}
		};
		
		return action;
	}

	/**
	 * @return a new {@link Action} allowing users to open the
	 *         {@link LogEventDetailDialog} for the currently selected
	 *         {@link LogEvent}
	 */
	private Action createDetailsAction() {
		final Action action = new Action("Show Selected Event Details...", PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD)) {
			@Override
			public void run() {
				final int index = _table.getSelectionIndex();
				if (index > -1) {
					openDetailDialog(_model.getEventAt(index));
				}
			}
		};
		action.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));
		action.setEnabled(false);
		_table.addSelectionListener(widgetSelectedAdapter(e -> action.setEnabled(e.item != null)));

		return action;
	}
	
	/**
	 * @return a new {@link Action} allowing users to copy the currently selected
	 *         {@link LogEvent} to clipboard
	 */
	private Action createCopyAction() {
		final Action action = new Action("Copy Selected Event", PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_COPY)) {
			@Override
			public void run() {
				final int index = _table.getSelectionIndex();
				if (index > -1) {
					final LogEvent event = _model.getEventAt(index);
					final Clipboard board = new Clipboard(_table.getDisplay());
					final Function<LogEvent, String> serializer = getEventSerializer();
					
					board.setContents(new Object[] { serializer.apply(event) }, new Transfer[] { TextTransfer.getInstance() });
					board.dispose(); // dispose the instance immediately
				}
			}
		};
		action.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_COPY_DISABLED));
		action.setEnabled(false);
		_table.addSelectionListener(widgetSelectedAdapter(e -> action.setEnabled(e.item != null)));

		return action;
	}

	/**
	 * @return a new {@link Action} allowing users to pause table updates
	 */
	private Action createPauseAction() {
		final Action action = new Action("Pause Event Table Refresh", Action.AS_CHECK_BOX) {
			@Override
			public void run() {
				// does nothing special
			}
		};
		action.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_PAUSE));
		
		return action;
	}

	/**
	 * @return a new {@link Action} allowing users to clear the table
	 */
	private Action createClearAction() {
		final Action action = new Action("Clear Event Table") {
			@Override
			public void run() {
				_model.clear();
			}
		};
		action.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_CLEAR));

		return action;
	}

	/**
	 * @return a new {@link Action} allowing users to start/stop the
	 *         {@link LogEvent} capturing server
	 */
	private Action createRunAction() {
		final Action action = new Action("Start Event Server", Action.AS_CHECK_BOX) {
			@Override
			public void run() {
				if (isChecked()) {
					_server.start();
				} else {
					_server.stop();
				}
			}
		};
		action.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_START));

		// listen to server state changes to update the action's icon
		_server.addServerListener(running -> {
			final ImageDescriptor img;
			final String text;
			if (running) {
				img = Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_STOP);
				text = "Stop Event Server";
			} else {
				img = Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_START);
				text = "Start Event Server";
			}
			action.setImageDescriptor(img);
			action.setText(text);
			action.setChecked(running);
		});

		return action;
	}

	/**
	 * @return the {@link Function} for serializing {@link LogEvent}s
	 */
	private Function<LogEvent, String> getEventSerializer() {
		return e -> {
			final StringBuilder out = new StringBuilder();
			out.append(LogEventProperty.TIMESTAMP.getValueProvider().apply(e)).append("\t");
			out.append(LogEventProperty.LEVEL.getValueProvider().apply(e)).append("\t");
			out.append(LogEventProperty.CATEGORY.getValueProvider().apply(e)).append(" ");
			out.append(LogEventProperty.MESSAGE.getValueProvider().apply(e));

			@SuppressWarnings("deprecation")
			final ThrowableProxy proxy = e.getThrownProxy();
			if (proxy != null) {
				out.append(System.lineSeparator());

				@SuppressWarnings("deprecation")
				final String stacktrace = proxy.getCauseStackTraceAsString("");
				out.append(stacktrace);
			}
			
			return out.toString();
		};
	}
	
	@Override
	public void setFocus() {
		// do not focus the viewer itself
	}

	@Override
	public void dispose() {
		// stop the server and pause event updates
		if (_server.isRunning()) _server.stop(); 

		// close all open dialogs
		if (_dialogs != null) _dialogs.close();
		
		// stop listening to preference changes
		if (_prefs != null) _prefs.removePropertyChangeListener(_prefListener); 
		
		// lastly, do whatever the super-class does
		super.dispose();
	}
}
