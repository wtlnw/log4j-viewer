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
