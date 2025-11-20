package org.wtlnw.eclipse.log4j.viewer.core.filter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JUnit tests for {@link LogEventFilter}.
 */
class TestLogEventFilter {

	@Test
	void test() {
		final LogEventPropertyFilter loggerFilter = new LogEventPropertyFilter(LogEventProperty.CATEGORY);
		loggerFilter.setPattern("logger");
		final LogEventPropertyFilter levelFilter = new LogEventPropertyFilter(LogEventProperty.LEVEL);
		levelFilter.setPattern("info");
		
		final LogEventFilter filter = new LogEventFilter();
		filter.getFilters().add(loggerFilter);
		filter.getFilters().add(levelFilter);
		
		final MutableLogEvent event = new MutableLogEvent();
		Assertions.assertFalse(filter.test(event));
		
		event.setLoggerName("logger");
		Assertions.assertFalse(filter.test(event));
		
		event.setLevel(Level.INFO);
		Assertions.assertTrue(filter.test(event));
	}

	@Test
	void testGetByProperty() {
		final LogEventPropertyFilter categoryFilter = new LogEventPropertyFilter(LogEventProperty.CATEGORY);
		final LogEventPropertyFilter levelFilter = new LogEventPropertyFilter(LogEventProperty.LEVEL);
		final LogEventFilter filter = new LogEventFilter();
		filter.getFilters().add(categoryFilter);
		filter.getFilters().add(levelFilter);

		Assertions.assertEquals(categoryFilter, filter.get(LogEventProperty.CATEGORY));
		Assertions.assertNull(filter.get(LogEventProperty.MESSAGE));
	}
}
