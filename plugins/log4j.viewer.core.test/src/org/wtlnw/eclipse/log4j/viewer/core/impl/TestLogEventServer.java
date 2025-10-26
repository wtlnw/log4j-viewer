package org.wtlnw.eclipse.log4j.viewer.core.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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

/**
 * Unit tests for {@link LogEventServer}.
 */
public class TestLogEventServer {

	private static final String CONFIG = """
			<Configuration xmlns="https://logging.apache.org/xml/ns"
			               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			               xsi:schemaLocation="
			                   https://logging.apache.org/xml/ns
			                   https://logging.apache.org/xml/ns/log4j-config-2.xsd">
			  <Appenders>
			    <Socket name="SOCKET_APPENDER" host="localhost" port="4445">
			      <SerializedLayout/>
			    </Socket>
			  </Appenders>
			  <Loggers>
			    <Root level="INFO">
			      <AppenderRef ref="SOCKET_APPENDER"/>
			    </Root>
			  </Loggers>
			</Configuration>
			""";

	@SuppressWarnings("deprecation")
	@Test
	void test() throws IOException {
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEventSupplierFactory> factories = List.of(new SerializedLogEventSupplierFactory());
		final LogEventServer server = new LogEventServer(events::add, factories);

		server.start();

		try (final LoggerContext context = Configurator.initialize(config(CONFIG))) {
			final Logger logger = context.getLogger(TestLogEventServer.class.getSimpleName());
			logger.info("Information message");
			logger.warn("Warning message");
			logger.error("Error message", new RuntimeException());
		}

		server.stop();
		
		Assertions.assertEquals(3, events.size());
		Assertions.assertEquals("Information message", events.get(0).getMessage().getFormattedMessage());
		Assertions.assertEquals("Warning message", events.get(1).getMessage().getFormattedMessage());
		Assertions.assertEquals("Error message", events.get(2).getMessage().getFormattedMessage());
		
		Assertions.assertNotNull(events.get(2).getThrownProxy());
	}

	@Test
	void testAcceptTimeout() throws IOException, InterruptedException {
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEventSupplierFactory> factories = List.of(new SerializedLogEventSupplierFactory());
		final LogEventServer server = new LogEventServer(events::add, factories);

		// first, start the server
		server.start();

		// wait for at least twice the length of server's timeout
		Thread.sleep(server.getTimeout());
		
		// now connect to the server
		try (final LoggerContext context = Configurator.initialize(config(CONFIG))) {
			final Logger logger = context.getLogger(TestLogEventServer.class.getSimpleName());
			logger.info("Information message");
			logger.warn("Warning message");
			logger.error("Error message", new RuntimeException());
		}

		// terminate the server and expect all three events having been received
		server.stop();

		Assertions.assertEquals(3, events.size());
	}
	
	@Test
	void testAcceptReadTimeout() throws IOException, InterruptedException {
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEventSupplierFactory> factories = List.of(new SerializedLogEventSupplierFactory());
		final LogEventServer server = new LogEventServer(events::add, factories);

		// first, start the server
		server.start();
		
		// connect to the server
		try (final LoggerContext context = Configurator.initialize(config(CONFIG))) {
			final Logger logger = context.getLogger(TestLogEventServer.class.getSimpleName());

			// wait for at least twice the length of server's timeout
			Thread.sleep(server.getTimeout());
			
			// now send the log messages
			logger.info("Information message");
			logger.warn("Warning message");
			logger.error("Error message", new RuntimeException());
		}

		// terminate the server and expect all three events having been received
		server.stop();

		Assertions.assertEquals(3, events.size());
	}
	
	@Test
	void testNoSuppliers() throws IOException, InterruptedException {
		final List<LogEvent> events = new ArrayList<>();
		final List<LogEventSupplierFactory> factories = List.of();
		final LogEventServer server = new LogEventServer(events::add, factories);

		// first, start the server
		server.start();
		
		// connect to the server
		try (final LoggerContext context = Configurator.initialize(config(CONFIG))) {
			final Logger logger = context.getLogger(TestLogEventServer.class.getSimpleName());

			// wait for at least twice the length of server's timeout
			Thread.sleep(server.getTimeout());
			
			// now send the log messages
			logger.info("Information message");
			logger.warn("Warning message");
			logger.error("Error message", new RuntimeException());
		}

		// terminate the server and expect no events having been received
		server.stop();

		Assertions.assertEquals(0, events.size());
	}
	
	private Configuration config(final String config) throws IOException {
		try (final InputStream input = new ByteArrayInputStream(config.getBytes())) {
			return ConfigurationFactory.getInstance().getConfiguration(null, new ConfigurationSource(input));
		}
	}
}
