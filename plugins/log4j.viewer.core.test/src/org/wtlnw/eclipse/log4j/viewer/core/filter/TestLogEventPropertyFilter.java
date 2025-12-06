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

package org.wtlnw.eclipse.log4j.viewer.core.filter;

import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JUnit tests for {@link LogEventPropertyFilter}.
 */
class TestLogEventPropertyFilter {

	/*
	 * A: CaseSensitive
	 * B: WholeWord
	 * C: Inverse
	 * D: Regex
	 */

	private LogEventPropertyFilter _filter;
	private MutableLogEvent _event;

	@BeforeEach
	void setup() {
		_filter = new LogEventPropertyFilter(LogEventProperty.CATEGORY);
		_event = new MutableLogEvent();
	}

	@AfterEach
	void cleanup() {
		_event = null;
		_filter = null;
	}

	private void assertPatternAndValue() {
		_event.setLoggerName("TestLogEventPropertyFilter");

		// negative case
		assertPattern("xyz", false);
		
		// case sensitivity testing
		assertPattern("Log", !_filter.isWholeWord());
		assertPattern("log", !_filter.isWholeWord() && !_filter.isMatchCase());
		assertPattern("LOG", !_filter.isWholeWord() && !_filter.isMatchCase());
		
		// whole word testing
		assertPattern("TestLogEventPropertyFilter", true);
		assertPattern("testLogEventPropertyFilter", !_filter.isMatchCase());
		
		// regex testing
		if (_filter.isRegularExpression()) {
			assertPattern("^Test.*", true);
			assertPattern("^Test", !_filter.isWholeWord());
		}
	}
	
	private void assertPattern(final String pattern, final boolean expected) {
		_filter.setPattern(pattern);

		if (_filter.isInverse()) {
			Assertions.assertNotEquals(expected, _filter.test(_event));
		} else {
			Assertions.assertEquals(expected, _filter.test(_event));
		}
	}
	
	private void assertEmptyPatternValue() {
		_event.setLoggerName("LogEventPropertyFilter");
		assertPattern("", !_filter.isWholeWord());
	}

	private void assertPatternNoValue() {
		_event.setLoggerName(null);
		assertPattern("xyz", false);

		_event.setLoggerName("");
		assertPattern("xyz", false);
	}

	private void assertEmptyPatternNoValue() {
		_event.setLoggerName(null);
		assertPattern("", true);

		_event.setLoggerName("");
		assertPattern("", true);
	}

	private void assertAll() {
		assertEmptyPatternNoValue();
		assertPatternNoValue();
		assertEmptyPatternValue();
		assertPatternAndValue();
	}
	
	@Test
	void notA_notB_notC_notD() {
		assertAll();
	}

	@Test
	void notA_notB_notC_D() {
		_filter.setRegularExpression(true);
		
		assertAll();
	}

	@Test
	void notA_notB_C_notD() {
		_filter.setInverse(true);

		assertAll();
	}

	@Test
	void notA_notB_C_D() {
		_filter.setInverse(true);
		_filter.setRegularExpression(true);

		assertAll();
	}

	@Test
	void notA_B_notC_notD() {
		_filter.setWholeWord(true);

		assertAll();
	}

	@Test
	void notA_B_notC_D() {
		_filter.setWholeWord(true);
		_filter.setRegularExpression(true);

		assertAll();
	}

	@Test
	void notA_B_C_notD() {
		_filter.setWholeWord(true);
		_filter.setInverse(true);

		assertAll();
	}

	@Test
	void notA_B_C_D() {
		_filter.setWholeWord(true);
		_filter.setInverse(true);
		_filter.setRegularExpression(true);
		
		assertAll();
	}

	@Test
	void A_notB_notC_notD() {
		_filter.setMatchCase(true);
		
		assertAll();
	}

	@Test
	void A_notB_notC_D() {
		_filter.setMatchCase(true);
		_filter.setRegularExpression(true);
		
		assertAll();
	}

	@Test
	void A_notB_C_notD() {
		_filter.setMatchCase(true);
		_filter.setInverse(true);

		assertAll();
	}

	@Test
	void A_notB_C_D() {
		_filter.setMatchCase(true);
		_filter.setInverse(true);
		_filter.setRegularExpression(true);

		assertAll();
	}

	@Test
	void A_B_notC_notD() {
		_filter.setMatchCase(true);
		_filter.setWholeWord(true);

		assertAll();
	}

	@Test
	void A_B_notC_D() {
		_filter.setMatchCase(true);
		_filter.setWholeWord(true);
		_filter.setRegularExpression(true);

		assertAll();
	}

	@Test
	void A_B_C_notD() {
		_filter.setMatchCase(true);
		_filter.setWholeWord(true);
		_filter.setInverse(true);

		assertAll();
	}

	@Test
	void A_B_C_D() {
		_filter.setMatchCase(true);
		_filter.setWholeWord(true);
		_filter.setInverse(true);
		_filter.setRegularExpression(true);

		assertAll();
	}

	@Test
	void failOnInvalidRegex() {
		_filter.setRegularExpression(true);
		Assertions.assertThrows(PatternSyntaxException.class, () -> _filter.setPattern("[abc"));

		_filter.setRegularExpression(false);
		_filter.setPattern("[abc");
		Assertions.assertThrows(PatternSyntaxException.class, () -> _filter.setRegularExpression(true));
	}
}
