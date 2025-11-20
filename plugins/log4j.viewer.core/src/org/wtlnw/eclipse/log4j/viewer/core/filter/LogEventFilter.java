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
