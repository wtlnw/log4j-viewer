package org.wtlnw.eclipse.log4j.viewer.ui.views;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.logging.log4j.core.LogEvent;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.window.WindowManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
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
import org.wtlnw.eclipse.log4j.viewer.ui.widgets.LogEventColumn;
import org.wtlnw.eclipse.log4j.viewer.ui.widgets.LogEventTable;

/**
 * A {@link ViewPart} implementation displaying log4j event entries.
 */
public class LogViewerPart extends ViewPart {

// TODO: implement copy to clipboard of selected lines
// TODO: implement export to file
// TODO: implement preserve selection
	
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
		_server = new LogEventServer(new LogEventSupplierRegistry().getFactories(), e -> {
			final Lock write = _lock.writeLock();

			write.lock();
			try {
				if (_buffer.depleted()) {
					// block this thread until we have updated the table (hence syncExec())
					PlatformUI.getWorkbench().getDisplay().syncExec(() -> updateTable(false));
				}
				_buffer.put(e);
			} finally {
				write.unlock();
			}
		});
		_server.addErrorListener((msg, ex) -> Platform.getLog(getClass()).error(msg, ex));
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
			final Lock read = _lock.readLock();

			read.lock();
			try {
				final Table table = _viewer.getTable();
				final TableItem item = (TableItem) e.item;
				final LogEvent event;
				
				// Workaround for https://github.com/eclipse-platform/eclipse.platform.swt/issues/139
				// when an event is sent for an item which is not displayed.
				// This happens when a table is cleared while having a selection.
				try {
					event = eventAt(table.indexOf(item));
				} catch (final IndexOutOfBoundsException ex) {
					// this shouldn't have happened
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
			} finally {
				read.unlock();
			}
		});
		_viewer.getTable().addMouseListener(MouseListener.mouseDoubleClickAdapter(e -> {
			final Table table = (Table) e.widget;
			final int selectionIndex = table.getSelectionIndex();

			// fast-path return on empty selection
			if (selectionIndex < 0) {
				return;
			}
			
			// resolve the event to display the details for
			final LogEvent event = eventAt(selectionIndex);

			// lookup an already opened dialog and bring it to front
			for (final Window window : _dialogs.getWindows()) {
				if (window instanceof LogEventDetailDialog dialog && dialog.getEvent() == event) {
					dialog.open();
					return;
				}
			}

			// create a new dialog and open it.
			final LogEventDetailDialog dialog = new LogEventDetailDialog(table.getShell(), event);
			_dialogs.add(dialog);
			dialog.open();
		}));
		
//		getSite().setSelectionProvider(viewer);
		createActions();
		hookContextMenu();
		contributeToActionBars();

		// start the server AFTER initialization is done
		if (_prefs.getBoolean(LogViewerPreferenceConstants.AUTOSTART)) {
			_server.start();
		}

		// start table update
		scheduleTableUpdate();
	}

	/**
	 * @param tableIndex the index of the {@link TableItem} to resolve the
	 *                   appropriate {@link LogEvent} for
	 * @return the {@link LogEvent} to be displayed by the {@link TableItem} at the
	 *         given index
	 */
	private LogEvent eventAt(final int tableIndex) {
		final int dataIndex = _tableData.getSize() - 1 - tableIndex;
		return _tableData.get(dataIndex);
	}
	
	private Function<ToolBar, ToolItem> createColumnFilter(final LogEventProperty property) {
		return bar -> {
			final ToolItem item = new ToolItem(bar, SWT.PUSH);
			item.setImage(Activator.getInstance().getImageRegistry().get(_filter.get(property) == null ? Activator.IMG_FILTER_INACTIVE : Activator.IMG_FILTER_ACTIVE));
			item.setToolTipText("Open Filter Dialog");
			item.addSelectionListener(widgetSelectedAdapter(e -> {
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
			}));

			return item;
		};
	}
	
	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				LogViewerPart.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(_viewer);
		_viewer.setMenu(menu);
//		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(final IMenuManager manager) {
//		manager.add(action1);
//		manager.add(new Separator());
//		manager.add(action2);
	}

	private void fillContextMenu(final IMenuManager manager) {
//		manager.add(action1);
//		manager.add(action2);
//		// Other plug-ins can contribute there actions here
//		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(final IToolBarManager manager) {
		manager.add(_run);
		manager.add(_pause);
		manager.add(_clear);
	}

	/**
	 * Create and initialize all actions the receiver or any of its parts require.
	 */
	private void createActions() {
		_run = new Action("Start Event Server", Action.AS_CHECK_BOX) {
			@Override
			public void run() {
				if (isChecked()) {
					_server.start();
				} else {
					_server.stop();
				}
			}
		};
		_run.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_START));
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
			_run.setImageDescriptor(img);
			_run.setText(text);
			_run.setChecked(running);
		});
		
		_clear = new Action() {
			@Override
			public void run() {
				final Lock write = _lock.writeLock();
				
				write.lock();
				try {
					_rawEvents.clear();
					_tableData.clear();
					_viewer.getTable().removeAll();
				} finally {
					write.unlock();
				}
			}
		};
		_clear.setText("Clear Event Table");
		_clear.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_CLEAR));
		
		_pause = new Action("Pause Event Table Refresh", Action.AS_CHECK_BOX) {
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
		_pause.setImageDescriptor(Activator.getInstance().getImageRegistry().getDescriptor(Activator.IMG_PAUSE));
	}

	/**
	 * Clear the table and re-populate it with the current data respecting the
	 * current filter.
	 */
	private void refreshTable() {
		final Lock write = _lock.writeLock();

		write.lock();
		try {
			// clear the events displayed in the table
			_tableData.clear();
			
			// re-filter the raw events and populate table data
			for (int i = 0; i < _rawEvents.getSize(); i++) {
				final LogEvent event = _rawEvents.get(i);
				if (_filter.test(event)) {
					_tableData.put(event);
				}
			}
			
			// update the table with new table data
			_viewer.getTable().setItemCount(_tableData.getSize());
			_viewer.getTable().clearAll();
		} finally {
			write.unlock();
		}
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

		// remember if there is at least one new event to be displayed
		boolean needsUpdate = false;

		_buffer.flip();
		while (!_buffer.depleted()) {
			final LogEvent event = _buffer.get();

			// unconditionally store raw events
			_rawEvents.put(event);

			// update table data if refresh is not paused
			if (_filter.test(event)) {
				_tableData.put(event);
				needsUpdate = true;
			}
		}
		_buffer.clear();

		// update the table
		if (_viewer != null && !_viewer.isDisposed()) {
			// only update the table if there is at least one new event
			// to display, thus avoiding unnecessary flickering.
			if (needsUpdate) {
				_viewer.getTable().setItemCount(_tableData.getSize());
				_viewer.getTable().clearAll();
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
			final Lock write = _lock.writeLock();

			write.lock();
			try {
				updateTable(true);
			} finally {
				write.unlock();
			}
		});
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
