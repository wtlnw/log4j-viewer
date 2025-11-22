package org.wtlnw.eclipse.log4j.viewer.ui.util;

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
}
