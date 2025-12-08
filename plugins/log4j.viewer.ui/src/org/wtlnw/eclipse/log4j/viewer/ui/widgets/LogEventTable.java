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

package org.wtlnw.eclipse.log4j.viewer.ui.widgets;

import java.util.function.Function;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * A {@link Composite} encapsulating a {@link Table} with a custom header to
 * allow for filtering columns directly from the table's column header.
 */
public class LogEventTable extends Composite {

	/**
	 * The number of pixels between two header entries.
	 */
	private static final int HEADER_SPACING = 3;

	/**
	 * The width of the {@link Sash} between two header entries.
	 * 
	 * <p>
	 * Note: make sure this is an odd number for the sash line to be drawn correctly!
	 * </p>
	 */
	private static final int SASH_WIDTH = 5;
	
	/**
	 * @see #getTable()
	 */
	private final Composite _header;

	/**
	 * @see #getHeader()
	 */
	private final Table _table;
	
	/**
	 * Create a {@link LogEventTable} with the given columns.
	 * 
	 * @param parent  see {@link #getParent()}
	 * @param columns the {@link LogEventColumn}s to create the table with
	 */
	public LogEventTable(final Composite parent, final LogEventColumn... columns) {
		super(parent, SWT.BORDER);
		
		final GridLayout layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		setLayout(layout);

		// NOTE: we do NOT assign the fields immediately in order to avoid
		// the possibility of calling initialization methods at the wrong
		// point in time, thus causing a NullPointerException during instantiation.
		// Instead, we assign the fields AFTER everything has been initialized.
		final Composite header = createHeader();
		header.getParent().setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		
		final Table table = createTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		table.getHorizontalBar().addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			final ScrollBar bar = (ScrollBar) e.widget;
			// do NOT use the ScrolledComposite.setOrigin() methods because these
			// always set the content's location to 0,0 if no scroll bars are
			// available.
			header.setLocation(new Point(-bar.getSelection(), 0));
		}));

		// create columns
		for (final LogEventColumn column : columns) {
			createColumn(column, header, table);
		}

		// now initialize both fields
		_header = header;
		_table = table;
	}

	private Composite createHeader() {
		final ScrolledComposite container = new ScrolledComposite(this, SWT.NONE);
		container.setExpandHorizontal(true);
		container.setExpandVertical(true);
		
		final RowLayout layout = new RowLayout();
		layout.marginLeft = layout.marginRight = 0;
		layout.marginTop = layout.marginBottom = 0;
		layout.marginWidth = layout.marginHeight = 0;
		layout.spacing = HEADER_SPACING;
		layout.fill = true;
		layout.wrap = false;

		final Composite header = new Composite(container, SWT.NONE);
		header.setLayout(layout);
		header.addListener(SWT.Resize, e -> container.setMinWidth(header.getSize().x));
		
		container.setContent(header);

		return header;
	}

	private Table createTable() {
		final Table table = new Table(this, SWT.FULL_SELECTION | SWT.VIRTUAL);
		table.setHeaderVisible(false);
		table.setLinesVisible(true);
		return table;
	}

	private void createColumn(final LogEventColumn column, final Composite header, final Table table) {
		checkWidget();
		
		if (table.getColumnCount() > 0) {
			createColumnSash(header, table);
		}
		
		final Composite columnHeader = createColumnHeader(column, header, table);
		columnHeader.setLayoutData(new RowData(columnHeader.computeSize(SWT.DEFAULT, SWT.DEFAULT)));
		
		final TableColumn columnItem = createColumnItem(column, table);
		
		columnHeader.addControlListener(ControlListener.controlResizedAdapter(e -> {
			int width = columnHeader.getSize().x;

			if (table.getColumnCount() > 1) {
				width += HEADER_SPACING;

				final int first = 0;
				final int last = table.getColumnCount() - 1;
				final int index = table.indexOf(columnItem);

				if (index == first) {
					width += SASH_WIDTH / 2 + 1;
				} else if (index == last) {
					width += SASH_WIDTH / 2;
				} else {
					width += HEADER_SPACING + SASH_WIDTH;
				}
			}

			columnItem.setWidth(width);
		}));
	}

	private void createColumnSash(final Composite header, final Table table) {
		final Control[] children = header.getChildren();
		final Composite columnHeader = (Composite) children[children.length - 1];
		final Point minSize = columnHeader.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		
		final Sash columnSash = new Sash(header, SWT.VERTICAL | SWT.SMOOTH);
		columnSash.setLayoutData(new RowData(SASH_WIDTH, table.getHeaderHeight()));
		columnSash.addMouseListener(new MouseListener() {

			/**
			 * The {@link MouseMoveListener} to be attached to the {@link Sash}
			 * when drag starts (upon mouse down event). It will be unregistered
			 * upon drag end (mouse up event).
			 */
			private final MouseMoveListener _move = e -> {
				final RowData data = (RowData) columnHeader.getLayoutData();

				// only update the size upon actual changes to avoid flickering
				if (e.x != 0) {
					data.width += e.x;

					// update the header size to adapt to the new column width
					header.setSize(header.computeSize(SWT.DEFAULT, minSize.y));
				}
			};
			
			@Override
			public void mouseDoubleClick(final MouseEvent e) {
				// ignore
			}

			@Override
			public void mouseDown(final MouseEvent e) {
				((Sash) e.widget).addMouseMoveListener(_move);
			}

			@Override
			public void mouseUp(final MouseEvent e) {
				((Sash) e.widget).removeMouseMoveListener(_move);
			}
		});
		columnSash.addPaintListener(e -> {
			final GC gc = e.gc;
			final Color backup = gc.getForeground();
			try {
				gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
				final Rectangle bounds = columnSash.getBounds();
				final int x = bounds.width / 2;
				final int y = bounds.height;
				gc.drawLine(x, 0, x, y);
			} finally {
				gc.setForeground(backup);
			}
		});
	}

	private Composite createColumnHeader(final LogEventColumn column, final Composite header, final Table table) {
		final GridLayout columnLayout = new GridLayout(2, false);
		columnLayout.marginWidth = columnLayout.marginHeight = 0;

		// add margin for the first column only, all other columns
		// will have the column sash as separator
		columnLayout.marginLeft = table.getColumnCount() == 0 ? SASH_WIDTH / 2 + 1 : 0;
		columnLayout.horizontalSpacing = 0;
		
		final Composite columnHead = new Composite(header, SWT.NONE);
		columnHead.setLayout(columnLayout);

		final Label label = new Label(columnHead, SWT.NONE);
		label.setFont(JFaceResources.getBannerFont());
		label.setText(column.getProperty().getName());
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final ToolBar bar = new ToolBar(columnHead, SWT.FLAT);
		bar.setLayoutData(new GridData(SWT.END, SWT.BEGINNING, false, false));

		final Function<ToolBar, ToolItem> factory = column.getFilterFactory();
		if (factory != null) {
			factory.apply(bar);
		}

		return columnHead;
	}

	private TableColumn createColumnItem(final LogEventColumn column, final Table table) {
		final TableColumn columnItem = new TableColumn(table, SWT.NONE);
		columnItem.setText(column.getProperty().getName());

		return columnItem;
	}

	/**
	 * @return the receiver's {@link Table}
	 */
	public Table getTable() {
		checkWidget();
		return _table;
	}

	/**
	 * @return the receiver's {@link Composite} containing custom header elements
	 */
	public Composite getHeader() {
		checkWidget();
		return _header;
	}
}
