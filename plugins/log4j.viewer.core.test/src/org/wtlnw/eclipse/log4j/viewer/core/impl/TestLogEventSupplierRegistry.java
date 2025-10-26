package org.wtlnw.eclipse.log4j.viewer.core.impl;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wtlnw.eclipse.log4j.viewer.core.api.LogEventSupplierFactory;

/**
 * Unit tests for {@link LogEventSupplierRegistry}.
 */
public class TestLogEventSupplierRegistry {

	@Test
	void test() {
		final LogEventSupplierRegistry registry = new LogEventSupplierRegistry();
		final List<LogEventSupplierFactory> factories = registry.getFactories();
		
		Assertions.assertTrue(!factories.isEmpty());
		Assertions.assertInstanceOf(SerializedLogEventSupplierFactory.class, factories.getFirst());
	}
}
