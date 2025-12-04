package log4j.viewer.core.xml.test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wtlnw.eclipse.log4j.viewer.core.api.LogEventSupplierFactory;
import org.wtlnw.eclipse.log4j.viewer.core.impl.LogEventServer;

import log4j.viewer.core.xml.impl.XmlLogEventSupplierFactory;

/**
 * Tests for {@link XmlLogEventSupplierFactory}.
 */
public class TestXmlLogEventSupplierFactory {

	private static final String CONFIG_COMPLETE = """
			<Configuration xmlns="https://logging.apache.org/xml/ns"
			               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			               xsi:schemaLocation="
			                   https://logging.apache.org/xml/ns
			                   https://logging.apache.org/xml/ns/log4j-config-2.xsd">
			  <Appenders>
			    <Socket name="SOCKET_APPENDER" host="localhost" port="4445">
			      <XmlLayout complete="true"/>
			    </Socket>
			  </Appenders>
			  <Loggers>
			    <Root level="INFO">
			      <AppenderRef ref="SOCKET_APPENDER"/>
			    </Root>
			  </Loggers>
			</Configuration>
			""";

	private static final String CONFIG_FRAGMENTS = """
			<Configuration xmlns="https://logging.apache.org/xml/ns"
			               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			               xsi:schemaLocation="
			                   https://logging.apache.org/xml/ns
			                   https://logging.apache.org/xml/ns/log4j-config-2.xsd">
			  <Appenders>
			    <Socket name="SOCKET_APPENDER" host="localhost" port="4445">
			      <XmlLayout complete="false"/>
			    </Socket>
			  </Appenders>
			  <Loggers>
			    <Root level="INFO">
			      <AppenderRef ref="SOCKET_APPENDER"/>
			    </Root>
			  </Loggers>
			</Configuration>
			""";

	@Test
	void testComplete() throws IOException, InterruptedException {
		runWithConfiguration(config(CONFIG_COMPLETE));
	}

	@Test
	void testFragments() throws IOException, InterruptedException {
		runWithConfiguration(config(CONFIG_FRAGMENTS));
	}
	
	@SuppressWarnings("deprecation")
	private void runWithConfiguration(final Configuration config) throws IOException, InterruptedException {
		// use a semaphore to block until the handler thread fails
		// which is a signal for us that either an error occurred or
		// end of stream was reached.
		final Semaphore sema = new Semaphore(0);
		
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEventSupplierFactory> factories = List.of(new XmlLogEventSupplierFactory());
		final List<Throwable> errors = new ArrayList<>();
		final LogEventServer server = new LogEventServer(factories, events::add);
		server.addErrorListener((msg, ex) -> {
			if (ex instanceof EOFException) {
				// ignore EOF
			} else {
				errors.add(ex);
			}
			sema.release();
		});

		server.start();

		try (final LoggerContext context = Configurator.initialize(config)) {
			final Logger logger = context.getLogger(TestXmlLogEventSupplierFactory.class.getSimpleName());
			logger.info("Information message");
			logger.warn("Warning message");
			logger.error("Error message", new RuntimeException());
		} catch (final Exception ex) {
			// logger initialization failed: make sure to release the
			// semaphore to allow the test to terminate
			sema.release();
		}

		sema.acquire();
		server.stop();
		
		if (!errors.isEmpty()) {
			errors.getFirst().printStackTrace();
			Assertions.fail();
		}
		Assertions.assertEquals(3, events.size());
		Assertions.assertEquals("Information message", events.get(0).getMessage().getFormattedMessage());
		Assertions.assertEquals("Warning message", events.get(1).getMessage().getFormattedMessage());
		Assertions.assertEquals("Error message", events.get(2).getMessage().getFormattedMessage());
		
		Assertions.assertNotNull(events.get(2).getThrownProxy());
	}

	private Configuration config(final String config) throws IOException {
		try (final InputStream input = new ByteArrayInputStream(config.getBytes())) {
			return ConfigurationFactory.getInstance().getConfiguration(null, new ConfigurationSource(input));
		}
	}
}
