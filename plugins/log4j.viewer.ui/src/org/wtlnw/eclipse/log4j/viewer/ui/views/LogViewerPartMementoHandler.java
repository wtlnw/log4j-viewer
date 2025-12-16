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

import java.util.Optional;

import org.eclipse.ui.IMemento;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventFilter;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventProperty;
import org.wtlnw.eclipse.log4j.viewer.core.filter.LogEventPropertyFilter;

/**
 * Instances of this class are responsible for handling various parts of a {@link LogViewerPart}'s
 * {@link IMemento} when saving or restoring the part's state.
 */
public class LogViewerPartMementoHandler {

	/**
	 * @see LogEventPropertyFilter#isEnabled()
	 */
	private static final String ATTR_ENABLED = "enabled";

	/**
	 * @see LogEventPropertyFilter#isWholeWord()
	 */
	private static final String ATTR_WHOLE_WORD = "wholeWord";

	/**
	 * @see LogEventPropertyFilter#isRegularExpression()
	 */
	private static final String ATTR_REGULAR_EXPRESSION = "regularExpression";

	/**
	 * @see LogEventPropertyFilter#isMatchCase()
	 */
	private static final String ATTR_MATCH_CASE = "matchCase";

	/**
	 * @see LogEventPropertyFilter#isInverse()
	 */
	private static final String ATTR_INVERSE = "inverse";

	/**
	 * @see LogEventPropertyFilter#getProperty()
	 */
	private static final String ATTR_PROPERTY = "property";
	
	/**
	 * @see LogEventFilter
	 */
	private static final String TAG_EVENT_FILTER = "LogEventFilter";

	/**
	 * @see LogEventPropertyFilter
	 */
	private static final String TAG_PROPERTY_FILTER = "LogEventPropertyFilter";

	/**
	 * @param memento the {@link IMemento} to restore the {@link LogEventFilter}
	 *                from or {@code null} if none is available
	 * @return the restored {@link LogEventFilter} or an empty one if none was
	 *         present or could be loaded
	 */
	public LogEventFilter loadFilter(final IMemento memento) {
		final LogEventFilter filter = new LogEventFilter();
		if (memento == null) {
			return filter;
		}
		
		final IMemento filterMemento = memento.getChild(TAG_EVENT_FILTER);
		if (filterMemento != null) {
			for (final IMemento propertyMemento : filterMemento.getChildren(TAG_PROPERTY_FILTER)) {
				final LogEventPropertyFilter propertyFilter = filter.get(LogEventProperty.valueOf(propertyMemento.getString(ATTR_PROPERTY)));
				// for compatibility with 1.0.x: since only enabled filters were 
				// stored back then, missing property is treated as true.
				propertyFilter.setEnabled(Optional.ofNullable(propertyMemento.getBoolean(ATTR_ENABLED)).orElse(Boolean.TRUE).booleanValue());
				propertyFilter.setInverse(propertyMemento.getBoolean(ATTR_INVERSE));
				propertyFilter.setMatchCase(propertyMemento.getBoolean(ATTR_MATCH_CASE));
				propertyFilter.setRegularExpression(propertyMemento.getBoolean(ATTR_REGULAR_EXPRESSION));
				propertyFilter.setWholeWord(propertyMemento.getBoolean(ATTR_WHOLE_WORD));
				propertyFilter.setPattern(Optional.ofNullable(propertyMemento.getTextData()).orElse(""));
			}
		}

		return filter;
	}

	/**
	 * Store the given {@link LogEventFilter} in the given {@link IMemento}.
	 * 
	 * @param filter  the {@link LogEventFilter} to store
	 * @param memento the {@link IMemento} to store the filter in
	 */
	public void saveFilter(final LogEventFilter filter, final IMemento memento) {
		final IMemento filterMemento = memento.createChild(TAG_EVENT_FILTER);
		for (final LogEventPropertyFilter propertyFilter : filter.getFilters()) {
			final IMemento propertyMemento = filterMemento.createChild(TAG_PROPERTY_FILTER);
			propertyMemento.putString(ATTR_PROPERTY, propertyFilter.getProperty().name());
			propertyMemento.putBoolean(ATTR_ENABLED, propertyFilter.isEnabled());
			propertyMemento.putBoolean(ATTR_INVERSE, propertyFilter.isInverse());
			propertyMemento.putBoolean(ATTR_MATCH_CASE, propertyFilter.isMatchCase());
			propertyMemento.putBoolean(ATTR_REGULAR_EXPRESSION, propertyFilter.isRegularExpression());
			propertyMemento.putBoolean(ATTR_WHOLE_WORD, propertyFilter.isWholeWord());
			propertyMemento.putTextData(propertyFilter.getPattern());
		}
	}
}
