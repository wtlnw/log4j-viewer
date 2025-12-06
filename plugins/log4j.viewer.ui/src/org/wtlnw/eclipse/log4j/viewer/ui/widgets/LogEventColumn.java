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

import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventProperty;

/**
 * Instances of this class provide functionality for creating columns for
 * {@link LogEventTable}s.
 */
public class LogEventColumn {

	/**
	 * @see #getProperty()
	 */
	private final LogEventProperty _property;

	/**
	 * @see #getFilterFactory()
	 */
	private Function<ToolBar, ToolItem> _filter;

	/**
	 * Create a {@link LogEventColumn}.
	 * 
	 * @param property see {@link #getProperty()}
	 */
	public LogEventColumn(final LogEventProperty property) {
		this(property, null);
	}

	/**
	 * Create a {@link LogEventColumn}.
	 * 
	 * @param property   see {@link #getProperty()}
	 * @param filterFactory see {@link #getFilterFactory()}
	 */
	public LogEventColumn(final LogEventProperty property, final Function<ToolBar, ToolItem> filterFactory) {
		_property = property;
		_filter = filterFactory;
	}
	
	/**
	 * @return the {@link LogEventProperty} the receiver represents
	 */
	public LogEventProperty getProperty() {
		return _property;
	}

	/**
	 * @return a {@link Function} creating a {@link ToolItem} for filter
	 *         functionality or {@code null} if column does not support filtering
	 */
	public Function<ToolBar, ToolItem> getFilterFactory() {
		return _filter;
	}
}
