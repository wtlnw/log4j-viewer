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

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.core.LogEvent;
import org.eclipse.swt.widgets.Table;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventFilter;
import org.wtlnw.eclipse.log4j.viewer.core.util.LogEventRingBuffer;

/**
 * Instances of this class represent a thread-safe model for the virtual
 * {@link Table} displaying {@link LogEvent}s.
 */
public class LogViewerTableModel {

	/**
	 * The {@link ReadWriteLock} to be used for synchronized access to the
	 * underlying event buffers.
	 */
    private final ReadWriteLock _lock = new ReentrantReadWriteLock();

	/**
	 * @see #getTable()
	 */
    private final Table _table;
    
	/**
	 * The {@link LogEventRingBuffer} instance containing unfiltered
	 * {@link LogEvent}s.
	 */
    private final LogEventRingBuffer _rawEvents;

	/**
	 * The {@link LogEventRingBuffer} instance containing filtered {@link LogEvent}s
	 * actually displayed in the table.
	 */
    private final LogEventRingBuffer _tableData;

	/**
	 * @see #getFilter()
	 */
    private volatile LogEventFilter _filter;

	/**
	 * This is the number of events added to the model via put since the last
	 * update.
	 */
    private volatile int _updates = 0;
    
	/**
	 * Create a {@link LogEventTableModel}.
	 * 
	 * @param table  see {@link #getTable()}
	 * @param size   the maximum number of captured {@link LogEvent}s to be
	 *               displayed
	 * @param filter see {@link #getFilter()}
	 */
	public LogViewerTableModel(final Table table, final int size, final LogEventFilter filter) {
		_table = Objects.requireNonNull(table);
		_filter = Objects.requireNonNull(filter);
		_rawEvents = new LogEventRingBuffer(size);
		_tableData = new LogEventRingBuffer(size);
	}

	/**
	 * @return the {@link Table} instance the receiver was created for
	 */
	public Table getTable() {
		return _table;
	}

	/**
	 * @return the {@link LogEventFilter} to be used for filtering captured
	 *         {@link LogEvent}s
	 */
	public LogEventFilter getFilter() {
		return _filter;
	}

	/**
	 * Setter for {@link #getFilter()}.
	 * 
	 * <p>
	 * Note: this method must only be called from the UI thread.
	 * </p>
	 *
	 * @param filter see {@link #getFilter()}
	 */
    public void setFilter(final LogEventFilter filter) {
        _filter = Objects.requireNonNull(filter);

        locking(_lock.writeLock(), () -> {
        	// try to preserve selection
        	final int oldTableIndex = _table.getSelectionIndex();
        	final LogEvent oldEvent = oldTableIndex < 0 ? null : getEventAt(oldTableIndex);
        	int newDataIndex = -1;

        	// reset update count and clear table data
        	_updates = 0;
        	_tableData.clear();

        	// re-populate table data using the new filter
        	for (int i = 0; i < _rawEvents.getSize(); i++) {
        		final LogEvent event = _rawEvents.get(i);
        		if (_filter.test(event)) {
        			_tableData.put(event);

        			// the previous selection is still visible -> restore it
        			if (event == oldEvent) {
        				newDataIndex = _tableData.getSize() - 1;
        			}
        		}
        	}

        	// update the table with new table data
        	_table.setItemCount(_tableData.getSize());
        	_table.clearAll();

        	// restore previous selection (if possible)
        	if (newDataIndex < 0) {
        		_table.deselectAll();
        	} else {
        		_table.select(invert(newDataIndex));
        	}
        });
    }

	/**
	 * <p>
	 * Note: this method is safe to be called from non-UI threads.
	 * </p>
	 * 
	 * @param row the index of the table row to resolve the appropriate
	 *            {@link LogEvent} for
	 * @return the {@link LogEvent} to be displayed by the table row at the given
	 *         index
	 */
    public LogEvent getEventAt(final int row) {
        return locking(_lock.readLock(), () -> _tableData.get(invert(row)));
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
	 * Add the given {@link LogEvent} to the receiver asynchronously.
	 * 
	 * <p>
	 * Note: this method is safe to be called from non-UI threads.
	 * </p>
	 *
	 * @param event the {@link LogEvent} to add
	 */
    public void put(final LogEvent event) {
        Objects.requireNonNull(event);

        locking(_lock.writeLock(), () -> {
        	// always record the raw event
            _rawEvents.put(event);

            // update the table if and only if the event is visible
            if (_filter.test(event)) {
                _tableData.put(event);
                _updates++;

                // asynchronously update the table in order to
                // keep the write lock for as short period of
                // time as possible.
                asyncUpdate();
            }
        });
    }

	/**
	 * Clear all captured {@link LogEvent}s and update the table.
	 * 
	 * <p>
	 * Note: this method must only be called from the UI thread.
	 * </p>
	 */
    public void clear() {
        locking(_lock.writeLock(), () -> {
        	_updates = 0;
            _rawEvents.clear();
            _tableData.clear();
            _table.removeAll();
        });
    }
	
	/**
	 * Apply the given {@link Consumer} to all currently displayed
	 * {@link LogEvent}s.
	 *
	 * <p>
	 * Note: this method is safe to be called from non-UI threads.
	 * </p>
	 *
	 * @param consumer the {@link Consumer} to apply
	 */
    public void forEach(final Consumer<LogEvent> consumer) {
        locking(_lock.readLock(), () -> {
            for (int i = _tableData.getSize() - 1; i >= 0; i--)  {
                consumer.accept(_tableData.get(i));
            }
        });
    }
	
	/**
	 * Execute the given {@link Supplier} instance synchronized by the given
	 * {@link Lock}.
	 *
	 * @param lock     the {@link Lock} to acquire prior to calling the given
	 *                 {@link Supplier}
	 * @param supplier the {@link Supplier} to call
	 * @return the supplied value
	 */
    private <T> T locking(final Lock lock, final Supplier<T> supplier) {
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

	/**
	 * Execute the given {@link Runnable} instance synchronized by the given
	 * {@link Lock}.
	 *
	 * @param lock     the {@link Lock} to acquire prior to calling the given
	 *                 {@link Runnable}
	 * @param runnable the {@link Runnable} to call
	 */
    private void locking(final Lock lock, final Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

	/**
	 * Update the table with current data.
	 */
    private void asyncUpdate() {
    	_table.getDisplay().asyncExec(() -> locking(_lock.readLock(), () -> {
    		// fast-path return on zero updates, which may happen in the
    		// following scenario:
    		// thread-1: put() -> trigger asyncUpdate() with _updates = 1
    		// thread-2: put() -> trigger asyncUpdate() with _updates = 2
    		// main: asyncUpdate is executed with _updates = 2, thus
    		//       making the second trigger obsolete
    		if (_updates < 1) {
    			return;
    		}

    		// remember the previous selection prior to updating the table
    		final int oldIndex = _table.getSelectionIndex();

    		// update item count and clear visible items
    		_table.setItemCount(_tableData.getSize());
    		_table.clearAll();

    		// advance the selection index by the number of updates
    		if (oldIndex > -1) {
    			final int newIndex = oldIndex + _updates;
    			if (newIndex < _table.getItemCount()) {
    				_table.select(newIndex);
    			} else {
    				_table.deselectAll();
    			}
    		}

    		// reset the number of updates
    		_updates = 0;
    	}));
    }
}
