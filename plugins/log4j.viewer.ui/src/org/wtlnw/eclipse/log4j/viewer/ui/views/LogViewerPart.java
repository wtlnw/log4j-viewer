package org.wtlnw.eclipse.log4j.viewer.ui.views;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.window.WindowManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
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
import org.wtlnw.eclipse.log4j.viewer.core.util.LogEventBuffer;
import org.wtlnw.eclipse.log4j.viewer.core.util.LogEventRingBuffer;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;
import org.wtlnw.eclipse.log4j.viewer.ui.dialogs.LogEventColumnFilterPopup;
import org.wtlnw.eclipse.log4j.viewer.ui.dialogs.LogEventDetailDialog;
import org.wtlnw.eclipse.log4j.viewer.ui.preferences.LogViewerPreferenceConstants;
import org.wtlnw.eclipse.log4j.viewer.ui.util.Util;
import org.wtlnw.eclipse.log4j.viewer.ui.widgets.LogEventColumn;
import org.wtlnw.eclipse.log4j.viewer.ui.widgets.LogEventTable;

/**
 * A {@link ViewPart} implementation displaying log4j event entries.
 */
public class LogViewerPart extends ViewPart {
	
	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.wtlnw.eclipse.log4j.viewer.ui.views.LogViewerPart";

	/**
	 * The {@link ReadWriteLock} to be used for synchronized access to internal data
	 * structures.
	 */
	private final ReadWriteLock _lock = new ReentrantReadWriteLock();
	
	private IPreferenceStore _prefs;
	private LogEventFilter _filter;
	private ColorRegistry _colors; 
	private LogEventTable _viewer;
	private LogEventServer _server;
	private LogEventBuffer _buffer;
	private LogEventRingBuffer _rawEvents;
	private LogEventRingBuffer _tableData;
	private int _refreshMillis;

	private Action _run;
	private Action _pause;
	private Action _clear;
	private Action _copy;
	private Action _details;
	private Action _export;

	private WindowManager _dialogs;

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
		_refreshMillis = _prefs.getInt(LogViewerPreferenceConstants.REFRESH);

		// load and initialize filter
		final LogViewerPartMementoHandler handler = new LogViewerPartMementoHandler();
		_filter = handler.loadFilter(memento);
		
		final int bufferSize = _prefs.getInt(LogViewerPreferenceConstants.BUFFER);
		_rawEvents = new LogEventRingBuffer(bufferSize);
		_tableData = new LogEventRingBuffer(bufferSize);
		_buffer = new LogEventBuffer(128); // use hard-coded value

		final int port = _prefs.getInt(LogViewerPreferenceConstants.PORT);
		final int timeout = _prefs.getInt(LogViewerPreferenceConstants.TIMEOUT);
		_server = new LogEventServer(port, timeout, new LogEventSupplierRegistry().getFactories(), e -> {
			Util.exclusive(_lock.writeLock(), () -> {
				if (_buffer.depleted()) {
					// block this thread until we have updated the table (hence syncExec())
					PlatformUI.getWorkbench().getDisplay().syncExec(() -> updateTable(false));
				}
				_buffer.put(e);
			});
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

		// initialize the detail dialog window manager
		_dialogs = new WindowManager();

		// initialize the actual event table
		final LogEventColumn[] columns = Stream.of(LogEventProperty.values())
					.map(p -> new LogEventColumn(p, createColumnFilter(p)))
					.toArray(LogEventColumn[]::new);

		_viewer = new LogEventTable(parent, columns);
		_viewer.getTable().addListener(SWT.SetData, e -> {
			final TableItem item = (TableItem) e.item;
			final LogEvent event = Util.exclusive(_lock.readLock(), () -> {
				// Workaround for https://github.com/eclipse-platform/eclipse.platform.swt/issues/139
				// when an event is sent for an item which is not displayed.
				// This happens when a table is cleared while having a selection.
				try {
					return eventAt(_viewer.getTable().indexOf(item));
				} catch (final IndexOutOfBoundsException ex) {
					// this shouldn't have happened
					return null;
				}
			});

			// ignore null-events resolved by the above workaround
			if (event == null) {
				return;
			}

			final String[] text = new String[columns.length];
			for (int i = 0; i < columns.length; i++) {
				// now this might seem weird but multi-line text seems to
				// affect the item's height on GTK by default as of November 2025;
				// we have to cut the text in a way that allows single-line
				// display of TableItems and display the multi-line text
				// as tool-tips.
				final String value = columns[i].getProperty().getValueProvider().apply(event);
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
		_viewer.getTable().addMouseListener(MouseListener.mouseDoubleClickAdapter(e -> {
			final LogEvent event = getSelectedEvent();
			if (event != null) {
				openDetailDialog(event);
			}
		}));
		
		createActions();
		fillContextMenu();
		fillActionBars();

		// start the server AFTER initialization is done
		if (_prefs.getBoolean(LogViewerPreferenceConstants.AUTOSTART)) {
			_server.start();
		}

		// start table update
		scheduleTableUpdate();
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
		final LogEventDetailDialog dialog = new LogEventDetailDialog(_viewer.getShell(), event);
		_dialogs.add(dialog);
		dialog.open();
	}

	/**
	 * @return the {@link LogEvent} currently selected in the table viewer or
	 *         {@code null} if none selected
	 */
	private LogEvent getSelectedEvent() {
		return Util.exclusive(_lock.readLock(), () -> {
			final int tableIndex = _viewer.getTable().getSelectionIndex();

			// fast-path return on empty selection
			if (tableIndex < 0) {
				return null;
			} else {
				return eventAt(tableIndex);
			}
		});
	}

	/**
	 * @param tableIndex the index of the {@link TableItem} to resolve the
	 *                   appropriate {@link LogEvent} for
	 * @return the {@link LogEvent} to be displayed by the {@link TableItem} at the
	 *         given index
	 */
	private LogEvent eventAt(final int tableIndex) {
		final int dataIndex = invert(tableIndex);
		return _tableData.get(dataIndex);
	}

	/**
	 * Invert the given index for accessing event buffer and table items.
	 * 
	 * @param index the index to invert
	 * @return the inverted index
	 */
	private int invert(final int index) {
		return _tableData.getSize() - 1 - index;
	}

	/**
	 * Build a {@link Function} which creates a {@link ToolItem} providing filtering
	 * functionality for the given {@link LogEventProperty}.
	 * 
	 * @param property the {@link LogEventProperty} to create a filter for
	 * @return the {@link Function} that will create a {@link ToolItem} for a
	 *         column's {@link ToolBar} opening a {@link LogEventColumnFilterPopup}
	 */
	private Function<ToolBar, ToolItem> createColumnFilter(final LogEventProperty property) {
		return bar -> {
			final ToolItem item = new ToolItem(bar, SWT.PUSH);
			item.setImage(Activator.getInstance().getImageRegistry().get(_filter.get(property) == null ? Activator.IMG_FILTER_INACTIVE : Activator.IMG_FILTER_ACTIVE));
			item.setToolTipText("Open Filter Dialog");
			item.addSelectionListener(widgetSelectedAdapter(e -> openFilterPopup(property, item)));

			return item;
		};
	}

	/**
	 * Open the filter popup dialog for the given property.
	 * 
	 * @param property the {@link LogEventProperty} to open the filter popup for
	 * @param item     the {@link ToolItem} on behalf of which the filter popup
	 *                 should be opened
	 */
	private void openFilterPopup(final LogEventProperty property, final ToolItem item) {
		final LogEventPropertyFilter filter = _filter.get(property);
		final LogEventColumnFilterPopup popup = new LogEventColumnFilterPopup(item, filter != null ? filter : new LogEventPropertyFilter(property));
		popup.create();
		popup.getShell().addDisposeListener(d -> {
			switch (popup.getReturnCode()) {
			case LogEventColumnFilterPopup.OK:
				// there was no filter for this property and the user applied a new one -> add it
				if (filter == null) {
					_filter.getFilters().add(popup.getFilter());
					item.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_FILTER_ACTIVE));
				} else {
					// otherwise, there is nothing to do because we made the changes in-line
				}
				refreshTable();
				break;
			case LogEventColumnFilterPopup.CLEAR:
				// remove the existing filter when the user clicked the clear button.
				if (filter != null) {
					_filter.getFilters().remove(filter);
					item.setImage(Activator.getInstance().getImageRegistry().get(Activator.IMG_FILTER_INACTIVE));
					refreshTable();
				}
				break;
			default:
				// dialog cancelled -> nothing to do
				break;
			}
		});
		popup.open();
	}

	/**
	 * Add a context menu to the viewer's table.
	 */
	private void fillContextMenu() {
		final MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(mgr -> {
			mgr.add(_copy);
			mgr.add(_details);
		});

		final Menu menu = menuMgr.createContextMenu(_viewer.getTable());
		_viewer.getTable().setMenu(menu);
	}
	
	/**
	 * Add actions to the view's actions bars.
	 */
	private void fillActionBars() {
		final IActionBars bars = getViewSite().getActionBars();

		// fill the pull-down menu (aka burger menu)
		final IMenuManager menu = bars.getMenuManager();
		menu.add(_export);

		// fill the tool bar
		final IToolBarManager toolbar = bars.getToolBarManager();
		toolbar.add(_run);
		toolbar.add(_pause);
		toolbar.add(_clear);
	}

	/**
	 * Create and initialize all actions the receiver or any of its parts require.
	 */
	private void createActions() {
		_run = createRunAction();
		_clear = createClearAction();
		_pause = createPauseAction();
		_copy = createCopyAction();
		_details = createDetailsAction();
		_export = createExportAction();
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
				final FileDialog dialog = new FileDialog(_viewer.getShell(), SWT.SAVE);
				dialog.setText(getText());
				dialog.setOverwrite(true);
				final String path = dialog.open();

				if (path != null) {
					try (final PrintWriter out = new PrintWriter(path)) {
						Util.exclusive(_lock.readLock(), () -> {
							final Function<LogEvent, String> serializer = getEventSerializer();
							for (int i = _tableData.getSize() - 1; i >= 0; i--) {
								out.println(serializer.apply(_tableData.get(i)));
							}
						});
					} catch (final FileNotFoundException e) {
						ErrorDialog.openError(_viewer.getShell(), getText(), null, Status.error("Failed to export displayed events to specified file.", e));
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
				final LogEvent event = getSelectedEvent();
				if (event != null) {
					openDetailDialog(event);
				}
			}
		};
		action.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));
		action.setEnabled(false);
		_viewer.getTable().addSelectionListener(widgetSelectedAdapter(e -> action.setEnabled(e.item != null)));

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
				final LogEvent event = getSelectedEvent();

				if (event != null) {
					final Clipboard board = new Clipboard(_viewer.getDisplay());
					final Function<LogEvent, String> serializer = getEventSerializer();
					
					board.setContents(new Object[] { serializer.apply(event) }, new Transfer[] { TextTransfer.getInstance() });
					board.dispose(); // dispose the instance immediately
				}
			}
		};
		action.setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_COPY_DISABLED));
		action.setEnabled(false);
		_viewer.getTable().addSelectionListener(widgetSelectedAdapter(e -> action.setEnabled(e.item != null)));

		return action;
	}

	/**
	 * @return a new {@link Action} allowing users to pause table updates
	 */
	private Action createPauseAction() {
		final Action action = new Action("Pause Event Table Refresh", Action.AS_CHECK_BOX) {
			@Override
			public void run() {
				if (isChecked()) {
					return;
				}

				// re-populate the table with current events
				refreshTable();

				// schedule periodic refresh
				scheduleTableUpdate();
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
				Util.exclusive(_lock.writeLock(), () -> {
					_rawEvents.clear();
					_tableData.clear();
					_viewer.getTable().removeAll();
				});
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
	 * Clear the table and re-populate it with the current data respecting the
	 * current filter.
	 */
	private void refreshTable() {
		Util.exclusive(_lock.writeLock(), () -> {
			// try to preserve selection
			final int oldTableIndex = _viewer.getTable().getSelectionIndex();
			final LogEvent oldEvent = oldTableIndex < 0 ? null : eventAt(oldTableIndex);
			int newDataIndex = -1;

			// clear the events displayed in the table
			_tableData.clear();

			// re-filter the raw events and populate table data
			for (int i = 0; i < _rawEvents.getSize(); i++) {
				final LogEvent event = _rawEvents.get(i);
				if (_filter.test(event)) {
					_tableData.put(event);

					// the previous selection is still visible -> restore it
					if (event == oldEvent) {
						newDataIndex = i;
					}
				}
			}

			// update the table with new table data
			_viewer.getTable().setItemCount(_tableData.getSize());
			_viewer.getTable().clearAll();

			// restore previous selection (if possible)
			if (newDataIndex < 0) {
				_viewer.getTable().deselectAll();
			} else {
				_viewer.getTable().select(invert(newDataIndex));
			}
		});
	}

	/**
	 * Update the table by displaying the new event entries respecting the current
	 * filter.
	 * 
	 * <p>
	 * Note: has no effect if updating was paused or no new entries were found.
	 * </p>
	 * 
	 * @param repeat {@code true} to automatically schedule the next call to this
	 *               method after the configured amount of milliseconds or
	 *               {@code false} to perform a one-time update only
	 */
	private void updateTable(final boolean repeat) {
		// updates are paused, just clear the buffer and proceed
		if (_pause.isChecked()) {
			_buffer.clear();
			return;
		}

		// keep track of new items to be displayed
		int added = 0;

		_buffer.flip();
		while (!_buffer.depleted()) {
			final LogEvent event = _buffer.get();

			// unconditionally store raw events
			_rawEvents.put(event);

			// update table data if refresh is not paused
			if (_filter.test(event)) {
				_tableData.put(event);
				added++;
			}
		}
		_buffer.clear();

		// update the table
		if (_viewer != null && !_viewer.isDisposed()) {
			// only update the table if there is at least one new event
			// to display, thus avoiding unnecessary flickering.
			if (added > 0) {
				// remember the current selection
				final int oldTableIndex = _viewer.getTable().getSelectionIndex();

				// update the table first
				_viewer.getTable().setItemCount(_tableData.getSize());
				_viewer.getTable().clearAll();

				// try to preserve the selection (if possible)
				if (oldTableIndex > -1) {
					final int newTableIndex = oldTableIndex + added;
					if (newTableIndex < _viewer.getTable().getItemCount()) {
						_viewer.getTable().select(newTableIndex);
					} else {
						_viewer.getTable().deselectAll();
					}
				}
			}

			// repeat after configured amount of time
			if (repeat) {
				scheduleTableUpdate();
			}
		}
	}

	/**
	 * Schedule a deferred call to {@link #updateTable(boolean)} in order
	 * to display new entries after the configured amount of milliseconds.
	 */
	private void scheduleTableUpdate() {
		_viewer.getDisplay().timerExec(_refreshMillis, () -> {
			Util.exclusive(_lock.writeLock(), () -> {
				updateTable(true);
			});
		});
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
		
		// lastly, do whatever the super-class does
		super.dispose();
	}
}
