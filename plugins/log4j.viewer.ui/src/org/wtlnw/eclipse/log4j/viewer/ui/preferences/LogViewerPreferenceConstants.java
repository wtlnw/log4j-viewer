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

package org.wtlnw.eclipse.log4j.viewer.ui.preferences;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Constant definitions for plug-in preferences.
 */
public interface LogViewerPreferenceConstants {

	/**
	 * The port to listen to incoming {@link LogEvent}s on.
	 */
	String PORT = "port";

	/**
	 * The timeout in milliseconds to block for when listening
	 * for incoming connection requests or data for.
	 */
	String TIMEOUT = "timeout";

	/**
	 * The number of entries to be displayed in the log event view.
	 */
	String BUFFER = "buffer";

	/**
	 * The flag indicating whether to automatically start listening
	 * for incoming events when the log event view is opened.
	 */
	String AUTOSTART = "autostart";

	/**
	 * The color to be used for displaying debug messages in the log view.
	 */
	String COLOR_DEBUG = "color-debug";

	/**
	 * The color to be used for displaying info messages in the log view.
	 */
	String COLOR_INFO = "color-info";

	/**
	 * The color to be used for displaying warning messages in the log view.
	 */
	String COLOR_WARN = "color-warn";

	/**
	 * The color to be used for displaying error messages in the log view.
	 */
	String COLOR_ERROR = "color-error";

	/**
	 * The color to be used for displaying fatal messages in the log view.
	 */
	String COLOR_FATAL = "color-fatal";
}
