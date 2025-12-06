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

package org.wtlnw.eclipse.log4j.viewer.ui.util;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Static utility functions.
 */
public class Util {

	/**
	 * @param string the {@link String} to return
	 * @param def    the {@link String} to return if the given one was {@code null}
	 * @return the given {@link String} or the given default value if it was
	 *         {@code null}
	 */
	public static String nonNullOrElse(final String string, final String def) {
		return string == null ? "" : string;
	}

	/**
	 * Execute the given {@link Runnable} exclusively using the given {@link Lock}.
	 * 
	 * @param lock the {@link Lock} to use for locking purposes
	 * @param code the {@link Runnable} to execute
	 */
	public static void exclusive(final Lock lock, final Runnable code) {
		lock.lock();
		try {
			code.run();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Execute the given {@link Supplier} exclusively using the given {@link Lock}.
	 * 
	 * @param <T>  the return type value of the given {@link Supplier}
	 * @param lock the {@link Lock} to use for locking purposes
	 * @param code the {@link Supplier} to execute
	 * @return the given {@link Supplier}'s result
	 */
	public static <T> T exclusive(final Lock lock, final Supplier<T> code) {
		lock.lock();
		try {
			return code.get();
		} finally {
			lock.unlock();
		}
	}
}
