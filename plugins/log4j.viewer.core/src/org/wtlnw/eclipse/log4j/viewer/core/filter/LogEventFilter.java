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

package org.wtlnw.eclipse.log4j.viewer.core.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Instances of this class provide a description for filtering {@link LogEvent}s according to their properties.
 */
public class LogEventFilter implements Predicate<LogEvent> {

	/**
	 * @see #getFilters()
	 */
	private final List<LogEventPropertyFilter> _filters = new ArrayList<>();

	/**
	 * @return a (possibly empty) modifiable {@link List} of receiver's {@link LogEventPropertyFilter}s
	 */
	public List<LogEventPropertyFilter> getFilters() {
		return _filters;
	}

	/**
	 * @param property the {@link LogEventProperty} to return the registered
	 *                 {@link LogEventPropertyFilter} for
	 * @return the {@link LogEventPropertyFilter} for the given
	 *         {@link LogEventProperty} or {@code null} if non was registered
	 */
	public LogEventPropertyFilter get(final LogEventProperty property) {
		for (final LogEventPropertyFilter filter : _filters) {
			if (filter.getProperty() == property) {
				return filter;
			}
		}
		
		return null;
	}
	
	@Override
	public boolean test(final LogEvent event) {
		for (final LogEventPropertyFilter filter : _filters) {
			if (!filter.test(event)) {
				return false;
			}
		}

		return true;
	}
}
