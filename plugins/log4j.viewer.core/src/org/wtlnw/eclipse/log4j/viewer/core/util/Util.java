package org.wtlnw.eclipse.log4j.viewer.core.util;

import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Static utility functions and constants.
 */
public class Util {

	/**
	 * The {@link DateTimeFormatter} to use for {@link LogEvent}'s time stamp formatting.
	 */
	public static final DateTimeFormatter FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME
			.withChronology(IsoChronology.INSTANCE)
			.withLocale(Locale.getDefault())
			.withZone(ZoneId.systemDefault());
}
