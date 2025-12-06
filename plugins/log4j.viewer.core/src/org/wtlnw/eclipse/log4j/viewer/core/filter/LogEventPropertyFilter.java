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

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Instances of this class provide a description for filtering {@link LogEvent}s according to their properties.
 * 
 * <p>
 * Note: this implementation treats {@code null} values as empty strings.
 * </p>
 */
public class LogEventPropertyFilter implements Predicate<LogEvent> {

	/**
	 * @see #getProperty()
	 */
	private final LogEventProperty _property;
	
	/**
	 * @see #isMatchCase()
	 */
	private boolean _matchCase = false;

	/**
	 * @see #isRegularExpression()
	 */
	private boolean _regex = false;

	/**
	 * @see #isWholeWord()
	 */
	private boolean _wholeWord = false;

	/**
	 * @see #isInverse()
	 */
	private boolean _inverse = false;

	/**
	 * The pattern to be used for filtering.
	 */
	private Pattern _pattern;
	
	/**
	 * Create a {@link LogEventPropertyFilter}.
	 * 
	 * @param property see {@link #getProperty()}
	 */
	public LogEventPropertyFilter(final LogEventProperty property) {
		_property = Objects.requireNonNull(property);
		_pattern = build("");
	}
	
	/**
	 * @return the {@link LogEventProperty} to filter
	 */
	public LogEventProperty getProperty() {
		return _property;
	}

	/**
	 * @return {@code true} to explicitly match character's case, {@code false} for
	 *         case-insensitive filtering
	 */
	public boolean isMatchCase() {
		return _matchCase;
	}

	/**
	 * Setter for {@link #isMatchCase()}.
	 * 
	 * @param matchCase see {@link #isMatchCase()}
	 */
	public void setMatchCase(final boolean matchCase) {
		_matchCase = matchCase;
		
		// re-build the pattern if it had already been built
		if (_pattern != null) {
			_pattern = build(_pattern.pattern());
		}
	}

	/**
	 * @return {@code true} to interpret {@link #getPattern()} as a regular expression,
	 *         {@code false} otherwise
	 */
	public boolean isRegularExpression() {
		return _regex;
	}

	/**
	 * Setter for {@link #isRegularExpression()}.
	 * 
	 * @param regex see {@link #isRegularExpression()}
	 */
	public void setRegularExpression(final boolean regex) {
		_regex = regex;

		// re-build the pattern if it had already been built
		if (_pattern != null) {
			_pattern = build(_pattern.pattern());
		}
	}

	/**
	 * @return {@code true} to require input to match the entire {@link #getPattern()},
	 *         {@code false} to use 'contain' semantics
	 */
	public boolean isWholeWord() {
		return _wholeWord;
	}

	/**
	 * Setter for {@link #isWholeWord()}.
	 * 
	 * @param wholeWord see {@link #isWholeWord()}
	 */
	public void setWholeWord(final boolean wholeWord) {
		_wholeWord = wholeWord;
	}

	/**
	 * @return {@code true} to invert filter result, {@code false} to use the filter
	 *         result as is
	 */
	public boolean isInverse() {
		return _inverse;
	}

	/**
	 * Setter for {@link #isInverse()}.
	 * 
	 * @param inverse see {@link #isInverse()}
	 */
	public void setInverse(final boolean inverse) {
		_inverse = inverse;
	}

	/**
	 * @return the filter {@link String}
	 */
	public String getPattern() {
		return _pattern.pattern();
	}

	/**
	 * Setter for {@link #getPattern()}.
	 * 
	 * @param pattern see {@link #getPattern()}
	 */
	public void setPattern(final String pattern) {
		_pattern = build(Objects.requireNonNull(pattern));
	}

	private Pattern build(final String pattern) {
		int flags = 0;
		
		// disable case sensitivity
		if (!_matchCase) {
			flags |= Pattern.CASE_INSENSITIVE;
		}

		// disable meta character parsing
		if (!_regex) {
			flags |= Pattern.LITERAL;
		}
		
		return Pattern.compile(pattern, flags);
	}

	@Override
	public boolean test(final LogEvent event) {
		final String value = _property.getValueProvider().apply(event);
		final Matcher matcher = _pattern.matcher(value == null ? "" : value);
		final boolean result = _wholeWord ? matcher.matches() : matcher.find();
		
		return _inverse ? !result : result;
	}
}
