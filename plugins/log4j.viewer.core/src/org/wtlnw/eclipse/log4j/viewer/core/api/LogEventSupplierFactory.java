package org.wtlnw.eclipse.log4j.viewer.core.api;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Implementing classes are responsible for reading data from an
 * {@link InputStream} and instantiate a {@link LogEventSupplier} based on it.
 */
public interface LogEventSupplierFactory {

	/**
	 * Try to read data from the given {@link InputStream} and instantiate a new
	 * {@link LogEventSupplier} of {@link LogEvent}s.
	 * 
	 * <p>
	 * When supplied data is not supported by the implementing class, the stream
	 * must be reset to the state before this method was called.
	 * </p>
	 * 
	 * @param stream the {@link InputStream} to instantiate a new
	 *               {@link LogEventSupplier} of {@link LogEvent}s for
	 * @return a {@link LogEventSupplier} of {@link LogEvent}s read from the given
	 *         {@link InputStream} or {@code null} if the receiver does not support
	 *         data in the given stream
	 */
	LogEventSupplier get(InputStream stream) throws IOException;

	/**
	 * Implementing classes are responsible for reading {@link LogEvent}s from an
	 * {@link InputStream}.
	 */
	public interface LogEventSupplier {
		
		/**
		 * @return the next {@link LogEvent} read from the associated
		 *         {@link InputStream}
		 * @throws IOException  if an error occurred while reading from the associated
		 *                      {@link InputStream}
		 * @throws EOFException to indicate end of stream
		 */
		LogEvent get() throws IOException, EOFException;
	}
}
