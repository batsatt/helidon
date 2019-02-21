/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common.logging;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Wrapper around a j.u.l. logger that provides convenience overloads of all the 'level name' methods (e.g. info(), fine(), etc.).
 * The additional methods support varargs and String.format() substitution, and each of these has an overload that also accepts
 * a Throwable.
 *
 * For example, this:
 * <pre>
 *     logger.log(Level.WARNING, "Foo '{0}', Bar '{1}'", new Object[]{aFoo, aBar});
 * </pre>
 *
 * can be replaced with:
 * <pre>
 *     logger.warning("Foo '%s', Bar '%s'", aFoo, aBar);
 * </pre>
 *
 *
 * Further, to include an exception in the first example above, the message must have already been substituted:
 * <pre>
 *     logger.log(Level.WARNING, substitutedMsg, thrown);
 * </pre>
 *
 * That call can be replaced with:
 * <pre>
 *     logger.warning(thrown, "Foo '%s', Bar '%s'", aFoo, aBar);
 * </pre>
 *
 * Finally, a 'warn' variant of all the warning methods is provided, so the above could be further shortened to:
 * <pre>
 *     logger.warn("Foo '%s', Bar '%s'", aFoo, aBar);
 *     logger.warn(thrown, "Foo '%s', Bar '%s'", aFoo, aBar);
 * </pre>
 * Instances are accessed via {@link Logging#getLog(String)} or {@link Logging#getLog(String, String)}
 *
 * @since 2019-01-29
 */
public class Log extends Logger {
    private final Logger delegate;
    private int messageCount;

    /**
     * Converts a log level name to a {@link Level} instance. In addition to the standard {@link Level} names, the following
     * aliases are supported:
     * <ul>
     * <li>"debug" maps to {@link Level#FINEST}</li>
     * <li>"warn" maps to {@link Level#WARNING}</li>
     * </ul>
     * Names are case <em>insensitive</em>.
     *
     * @param levelName The name.
     * @return The level.
     */
    public static Level toLevel(final String levelName) {
        if (levelName.equalsIgnoreCase("debug")) {
            return Level.FINEST;
        } else if (levelName.equalsIgnoreCase("warn") || levelName.equalsIgnoreCase("warning")) {
            return Level.WARNING;
        } else {
            return Level.parse(levelName.toUpperCase());
        }
    }

    Log(final Logger delegate) {
        super(delegate.getName(), null);
        this.delegate = delegate;
    }

    /**
     * Returns the underlying {@link Logger}.
     *
     * @return The logger.
     */
    public Logger getDelegate() {
        return delegate;
    }

    @Override
    public ResourceBundle getResourceBundle() {
        return delegate.getResourceBundle();
    }

    @Override
    public String getResourceBundleName() {
        return delegate.getResourceBundleName();
    }

    @Override
    public void setFilter(Filter newFilter) throws SecurityException {
        delegate.setFilter(newFilter);
    }

    @Override
    public Filter getFilter() {
        return delegate.getFilter();
    }

    @Override
    public void log(LogRecord record) {
        delegate.log(record);
        messageCount++;
    }

    @Override
    public void log(Level level, String msg) {
        delegate.log(level, msg);
        messageCount++;
    }

    @Override
    public void log(Level level, Supplier<String> msgSupplier) {
        delegate.log(level, msgSupplier);
        messageCount++;
    }

    @Override
    public void log(Level level, String msg, Object param1) {
        delegate.log(level, msg, param1);
        messageCount++;
    }

    @Override
    public void log(Level level, String msg, Object[] params) {
        delegate.log(level, msg, params);
        messageCount++;
    }

    @Override
    public void log(Level level, String msg, Throwable thrown) {
        delegate.log(level, msg, thrown);
        messageCount++;
    }

    @Override
    public void log(Level level, Throwable thrown, Supplier<String> msgSupplier) {
        delegate.log(level, thrown, msgSupplier);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        delegate.logp(level, sourceClass, sourceMethod, msg);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, Supplier<String> msgSupplier) {
        delegate.logp(level, sourceClass, sourceMethod, msgSupplier);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        delegate.logp(level, sourceClass, sourceMethod, msg, param1);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        delegate.logp(level, sourceClass, sourceMethod, msg, params);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        delegate.logp(level, sourceClass, sourceMethod, msg, thrown);
        messageCount++;
    }

    @Override
    public void logp(Level level, String sourceClass, String sourceMethod, Throwable thrown, Supplier<String> msgSupplier) {
        delegate.logp(level, sourceClass, sourceMethod, thrown, msgSupplier);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object[] params) {
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, ResourceBundle bundle, String msg, Object... params) {
        delegate.logrb(level, sourceClass, sourceMethod, bundle, msg, params);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Throwable thrown) {
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
        messageCount++;
    }

    @Override
    public void logrb(Level level, String sourceClass, String sourceMethod, ResourceBundle bundle, String msg, Throwable thrown) {
        delegate.logrb(level, sourceClass, sourceMethod, bundle, msg, thrown);
        messageCount++;
    }

    @Override
    public void entering(String sourceClass, String sourceMethod) {
        delegate.entering(sourceClass, sourceMethod);
        messageCount++;
    }

    @Override
    public void entering(String sourceClass, String sourceMethod, Object param1) {
        delegate.entering(sourceClass, sourceMethod, param1);
        messageCount++;
    }

    @Override
    public void entering(String sourceClass, String sourceMethod, Object[] params) {
        delegate.entering(sourceClass, sourceMethod, params);
        messageCount++;
    }

    @Override
    public void exiting(String sourceClass, String sourceMethod) {
        delegate.exiting(sourceClass, sourceMethod);
        messageCount++;
    }

    @Override
    public void exiting(String sourceClass, String sourceMethod, Object result) {
        delegate.exiting(sourceClass, sourceMethod, result);
        messageCount++;
    }

    @Override
    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        delegate.throwing(sourceClass, sourceMethod, thrown);
        messageCount++;
    }

    @Override
    public void severe(String msg) {
        delegate.severe(msg);
        messageCount++;
    }

    /**
     * Log a severe message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void severe(String msg, final Object... args) {
        delegate.severe(format(msg, args));
        messageCount++;
    }

    /**
     * Log a severe message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void severe(Throwable thrown, String msg, final Object... args) {
        delegate.log(Level.SEVERE, format(msg, args), thrown);
        messageCount++;
    }

    @Override
    public void warning(String msg) {
        delegate.warning(msg);
        messageCount++;
    }

    /**
     * Log a warning message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void warning(String msg, final Object... args) {
        delegate.warning(format(msg, args));
        messageCount++;
    }

    /**
     * Log a warning message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void warning(Throwable thrown, String msg, final Object... args) {
        delegate.log(Level.WARNING, format(msg, args), thrown);
        messageCount++;
    }

    /**
     * Log a warning message.
     *
     * @param msg Message to be logged.
     */
    public void warn(String msg) {
        delegate.warning(msg);
        messageCount++;
    }

    /**
     * Log a warning message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void warn(String msg, final Object... args) {
        delegate.warning(format(msg, args));
        messageCount++;
    }

    /**
     * Log a warning message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void warn(Throwable thrown, String msg, final Object... args) {
        delegate.log(Level.WARNING, format(msg, args), thrown);
        messageCount++;
    }

    @Override
    public void info(String msg) {
        delegate.info(msg);
        messageCount++;
    }

    /**
     * Log an info message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void info(String msg, final Object... args) {
        delegate.info(format(msg, args));
        messageCount++;
    }

    /**
     * Log an info message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void info(Throwable thrown, String msg, final Object... args) {
        delegate.log(Level.INFO, format(msg, args), thrown);
        messageCount++;
    }

    @Override
    public void config(String msg) {
        delegate.config(msg);
        messageCount++;
    }

    /**
     * Log a config message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void config(String msg, final Object... args) {
        if (delegate.isLoggable(Level.CONFIG)) {
            delegate.config(format(msg, args));
            messageCount++;
        }
    }

    /**
     * Log a config message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void config(Throwable thrown, String msg, final Object... args) {
        if (delegate.isLoggable(Level.CONFIG)) {
            delegate.log(Level.CONFIG, format(msg, args), thrown);
            messageCount++;
        }
    }

    @Override
    public void fine(String msg) {
        delegate.fine(msg);
        messageCount++;
    }

    /**
     * Log a fine message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void fine(String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINE)) {
            delegate.fine(format(msg, args));
            messageCount++;
        }
    }

    /**
     * Log a fine message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void fine(Throwable thrown, String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINE)) {
            delegate.log(Level.FINE, format(msg, args), thrown);
            messageCount++;
        }
    }

    /**
     * Log a debug message.
     *
     * @param msg Mesasge to be logged.
     */
    public void debug(String msg) {
        delegate.fine(msg);
        messageCount++;
    }

    /**
     * Log a debug message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void debug(String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINE)) {
            delegate.fine(format(msg, args));
            messageCount++;
        }
    }

    /**
     * Log a debug message with associated throwable.
     *
     * @param thrown Associated throwable information.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void debug(Throwable thrown, String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINE)) {
            delegate.log(Level.FINE, format(msg, args), thrown);
            messageCount++;
        }
    }

    @Override
    public void finer(String msg) {
        delegate.finer(msg);
        messageCount++;
    }

    /**
     * Log a finer message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void finer(String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINER)) {
            delegate.finer(format(msg, args));
            messageCount++;
        }
    }

    /**
     * Log a finer message with associated throwable.
     *
     * @param thrown Associated throwable.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void finer(Throwable thrown, String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINER)) {
            delegate.log(Level.FINER, format(msg, args), thrown);
            messageCount++;
        }
    }

    @Override
    public void finest(final String msg) {
        delegate.finest(msg);
        messageCount++;
    }

    /**
     * Log a finest message.
     *
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void finest(final String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINEST)) {
            delegate.finest(format(msg, args));
            messageCount++;
        }
    }

    /**
     * Log a finest message with associated throwable information.
     *
     * @param thrown Associated throwable.
     * @param msg Message to be logged. Format string is allowed.
     * @param args Format string arguments.
     */
    public void finest(final Throwable thrown, final String msg, final Object... args) {
        if (delegate.isLoggable(Level.FINEST)) {
            delegate.log(Level.FINEST, format(msg, args), thrown);
            messageCount++;
        }
    }

    @Override
    public void severe(Supplier<String> msgSupplier) {
        delegate.severe(msgSupplier);
        messageCount++;
    }

    @Override
    public void warning(Supplier<String> msgSupplier) {
        delegate.warning(msgSupplier);
        messageCount++;
    }

    /**
     * Log a warning message using a message supplier function.
     *
     * @param msgSupplier Message supplier function.
     */
    public void warn(Supplier<String> msgSupplier) {
        delegate.warning(msgSupplier);
        messageCount++;
    }

    /**
     * Log an empty message, at INFO.
     */
    public void line() {
        delegate.info("");
        messageCount++;
    }

    /**
     * Log a line composed of the given character and length, at INFO.
     *
     * @param lineChar The repeating character.
     * @param lineLength The line length.
     */
    public void line(final char lineChar, final int lineLength) {
        delegate.info(toFilledString(lineChar, lineLength));
        messageCount++;
    }

    /**
     * Returns a string filled with the given character.
     *
     * @param repeatChar The character to repeat.
     * @param length The number of characters to repeat.
     * @return The string.
     */
    public static String toFilledString(final char repeatChar, int length) {
        final char[] filled = new char[length];
        Arrays.fill(filled, repeatChar);
        return new String(filled);
    }

    /**
     * Log a header with the given border character.
     *
     * @param border The border character.
     * @param msg The message.
     * @param args The message arguments.
     */
    public void header(final char border, final String msg, final Object... args) {
        final String edge = new String(new char[]{border, border, border});
        final String message = String.format(msg, args);
        final int trimLen = message.length() + 8;
        line(border, trimLen);
        info("%s %s %s", edge, message, edge);
        line(border, trimLen);
    }

    /**
     * Returns the number of log messages written to this instance.
     *
     * @return The messageCount.
     */
    public int messageCount() {
        return messageCount;
    }

    @Override
    public void info(Supplier<String> msgSupplier) {
        delegate.info(msgSupplier);
        messageCount++;
    }

    @Override
    public void config(Supplier<String> msgSupplier) {
        delegate.config(msgSupplier);
        messageCount++;
    }

    @Override
    public void fine(Supplier<String> msgSupplier) {
        delegate.fine(msgSupplier);
        messageCount++;
    }

    @Override
    public void finer(Supplier<String> msgSupplier) {
        delegate.finer(msgSupplier);
        messageCount++;
    }

    @Override
    public void finest(Supplier<String> msgSupplier) {
        delegate.finest(msgSupplier);
        messageCount++;
    }

    @Override
    public void setLevel(Level newLevel) throws SecurityException {
        delegate.setLevel(newLevel);
    }

    @Override
    public Level getLevel() {
        return delegate.getLevel();
    }

    @Override
    public boolean isLoggable(Level level) {
        return delegate.isLoggable(level);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void addHandler(Handler handler) throws SecurityException {
        delegate.addHandler(handler);
    }

    @Override
    public void removeHandler(Handler handler) throws SecurityException {
        delegate.removeHandler(handler);
    }

    @Override
    public Handler[] getHandlers() {
        return delegate.getHandlers();
    }

    @Override
    public void setUseParentHandlers(boolean useParentHandlers) {
        delegate.setUseParentHandlers(useParentHandlers);
    }

    @Override
    public boolean getUseParentHandlers() {
        return delegate.getUseParentHandlers();
    }

    @Override
    public void setResourceBundle(ResourceBundle bundle) {
        delegate.setResourceBundle(bundle);
    }

    @Override
    public Logger getParent() {
        return delegate.getParent();
    }

    @Override
    public void setParent(Logger parent) {
        delegate.setParent(parent);
    }

    private static String format(final String message, final Object... args) {
        return message == null ? "null" : String.format(message, args);
    }
}
