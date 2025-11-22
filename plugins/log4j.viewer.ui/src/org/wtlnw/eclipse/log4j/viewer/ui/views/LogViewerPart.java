package org.wtlnw.eclipse.log4j.viewer.ui.views;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.List;
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

public class LogViewerPart extends ViewPart {

//	TODO: implement copy to clipboard of selected lines
	
	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.wtlnw.eclipse.log4j.viewer.ui.views.LogViewerPart";

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
			synchronized (_buffer) {
				if (_buffer.depleted()) {
					PlatformUI.getWorkbench().getDisplay().syncExec(() -> updateTable(false));
				}
				_buffer.put(e);
			}
		});
		_server.addErrorListener((msg, ex) -> Platform.getLog(getClass()).error(msg, ex));
	}
	
	@Override
	public void createPartControl(Composite parent) {
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
			synchronized (_rawEvents) {
				final Table table = _viewer.getTable();
				final TableItem item = (TableItem) e.item;
				
				// inverse order
				final LogEvent event = eventAt(table.indexOf(item));
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
		makeActions();
		hookContextMenu();
		contributeToActionBars();

		// start the server AFTER initialization is done
		if (_prefs.getBoolean(LogViewerPreferenceConstants.AUTOSTART)) {
			_server.start();
		}

		// start table update
		scheduleTableUpdate();
	}

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

	private void fillLocalPullDown(IMenuManager manager) {
//		manager.add(action1);
//		manager.add(new Separator());
//		manager.add(action2);
	}

	private void fillContextMenu(IMenuManager manager) {
//		manager.add(action1);
//		manager.add(action2);
//		// Other plug-ins can contribute there actions here
//		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(_run);
		manager.add(_pause);
		manager.add(_clear);
	}

	private void makeActions() {
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
				synchronized (_rawEvents) {
					_rawEvents.clear();
					_tableData.clear();
					_viewer.getTable().removeAll();
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

	private void refreshTable() {
		synchronized (_rawEvents) {
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
		}
	}

	private void updateTable(final boolean repeat) {
		synchronized (_rawEvents) {
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
	}

	private void scheduleTableUpdate() {
		_viewer.getDisplay().timerExec(_refreshMillis, () -> {
			synchronized (_buffer) {
				updateTable(true);
			}
		});
	}
	
	@Override
	public void setFocus() {
		_viewer.setFocus();
	}

	@Override
	public void dispose() {
		// stop the server and pause event updates
		if (_server.isRunning()) _server.stop(); 
		
		// lastly, do whatever the super-class does
		super.dispose();
	}
}
