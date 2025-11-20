package org.wtlnw.eclipse.log4j.viewer.core.util;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JUnit tests for {@link LogEventRingBuffer}.
 */
class TestLogEventRingBuffer {

	@Test
	void test() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new LogEventRingBuffer(0));
		
		final LogEvent event1 = new MutableLogEvent();
		final LogEvent event2 = new MutableLogEvent();
		final LogEvent event3 = new MutableLogEvent();
		final LogEvent event4 = new MutableLogEvent();
		final LogEvent event5 = new MutableLogEvent();
		final LogEvent event6 = new MutableLogEvent();

		final LogEventRingBuffer buffer = new LogEventRingBuffer(3);
		Assertions.assertEquals(0, buffer.getSize());
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0));

		buffer.put(event1);
		Assertions.assertEquals(1, buffer.getSize());
		Assertions.assertEquals(event1, buffer.get(0));

		buffer.put(event2);
		buffer.put(event3);

		// buffer is full now, no entry has been overwritten yet
		Assertions.assertEquals(3, buffer.getSize());
		Assertions.assertEquals(event1, buffer.get(0));
		Assertions.assertEquals(event2, buffer.get(1));
		Assertions.assertEquals(event3, buffer.get(2));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(3));

		// wrap around of buffer's head, no wrap around of buffer's tail
		buffer.put(event4);
		Assertions.assertEquals(event2, buffer.get(0));
		Assertions.assertEquals(event3, buffer.get(1));
		Assertions.assertEquals(event4, buffer.get(2));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(3));

		// wrap around of buffer's tail
		buffer.put(event5);
		buffer.put(event6);
		Assertions.assertEquals(event4, buffer.get(0));
		Assertions.assertEquals(event5, buffer.get(1));
		Assertions.assertEquals(event6, buffer.get(2));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(3));
	}
}
