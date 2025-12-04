package log4j.viewer.core.json.impl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.jackson.Log4jJsonObjectMapper;
import org.wtlnw.eclipse.log4j.viewer.core.api.LogEventSupplierFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;

/**
 * {@link LogEventSupplierFactory} implementation for JsonLayout based {@link LogEvent}s.
 */
public class JsonLogEventSupplierFactory implements LogEventSupplierFactory {

	@Override
	public LogEventSupplier get(final InputStream stream) throws IOException {
		if (!stream.markSupported()) {
			throw new IllegalArgumentException();
		}

		stream.mark(4);
		try {
			final byte[] bytes = stream.readNBytes(4);
			final String string = new String(bytes).trim();
			
			if (string.startsWith("{")) {
				// make sure to reset the stream prior to supplier initialization
				stream.reset();

				// initialize the reader with the reset stream for reading multiple events
				final MappingIterator<LogEvent> reader = new Log4jJsonObjectMapper()
						.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readerFor(Log4jLogEvent.class)
						.readValues(stream);

				// that's the actual LogEventSupplier implementation as lambda
				return () -> {
					try {
						if (reader.hasNext()) {
							return reader.next();
						}
					} catch (final RuntimeException ex) {
						// make sure to wrap RuntimeExceptions in an IOException
						// to terminate the handler thread correctly
						throw new IOException(ex);
					}
					
					// when we get here, we're done reading from the stream
					throw new EOFException();
				};
			}
		} catch (final Exception ex) {
			// failed to read from the stream
		} finally {
			// make sure to reset the stream upon successful return
			stream.reset();
		}

		// either we do not support the stream or we failed reading from it
		return null;
	}
}
