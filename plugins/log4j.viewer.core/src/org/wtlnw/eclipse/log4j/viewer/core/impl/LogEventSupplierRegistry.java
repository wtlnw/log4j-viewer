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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.wtlnw.eclipse.log4j.viewer.core.api.LogEventSupplierFactory;

/**
 * Instances of this class are responsible for retrieving all {@link LogEventSupplierFactory}
 * implementations provided as extensions of this plugin.
 */
public class LogEventSupplierRegistry {

	/**
	 * The identifier of the extension point to be used for providing additional
	 * {@link LogEventSupplierFactory} implementations.
	 */
	public static final String EXTENSION_POINT_ID = "org.wtlnw.eclipse.log4j.viewer.core.events";

	/**
	 * @return a (possibly empty) {@link List} of registered
	 *         {@link LogEventSupplierFactory} instances
	 */
	public List<LogEventSupplierFactory> getFactories() {
		final List<LogEventSupplierFactory> factories = new ArrayList<>();
		final IExtensionRegistry registry = Platform.getExtensionRegistry();
		final IExtensionPoint point = registry.getExtensionPoint(EXTENSION_POINT_ID);

		for (final IExtension extension : point.getExtensions()) {
			for (final IConfigurationElement element : extension.getConfigurationElements()) {
				if ("supplier-factory".equals(element.getName())) {
					try {
						final LogEventSupplierFactory factory = (LogEventSupplierFactory) element.createExecutableExtension("class");
						factories.add(factory);
					} catch (Exception e) {
						Platform.getLog(LogEventSupplierRegistry.class).error("Failed to instantiate extension.", e);
					}
				}
			}
		}
		
		return factories;
	}
}
