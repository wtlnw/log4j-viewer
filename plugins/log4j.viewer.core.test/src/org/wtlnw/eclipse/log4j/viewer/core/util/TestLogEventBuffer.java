package org.wtlnw.eclipse.log4j.viewer.core.util;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JUnit tests for {@link LogEventBuffer}.
 */
class TestLogEventBuffer {

	@Test
	void test() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new LogEventBuffer(0));
		
		final LogEventBuffer buffer = new LogEventBuffer(3);
		
		Assertions.assertFalse(buffer.depleted());
		Assertions.assertTrue(buffer.flip().depleted());
		Assertions.assertFalse(buffer.clear().depleted());

		final MutableLogEvent event1 = new MutableLogEvent();
		buffer.put(event1);
		Assertions.assertFalse(buffer.depleted());
		Assertions.assertEquals(event1, buffer.flip().get());
		Assertions.assertTrue(buffer.depleted());
		Assertions.assertThrows(BufferUnderflowException.class, buffer::get);

		final MutableLogEvent event2 = new MutableLogEvent();
		final MutableLogEvent event3 = new MutableLogEvent();
		buffer.clear();
		buffer.put(event1);
		buffer.put(event2);
		buffer.put(event3);
		Assertions.assertTrue(buffer.depleted());

		final MutableLogEvent event4 = new MutableLogEvent();
		Assertions.assertThrows(BufferOverflowException.class, () -> buffer.put(event4));
	}
}
