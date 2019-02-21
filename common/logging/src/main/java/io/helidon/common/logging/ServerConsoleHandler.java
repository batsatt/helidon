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

import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import io.helidon.config.Config;

/**
 * A configurable {@link ConsoleHandler} that defaults to {@link System#out} rather than {@link System#err}.
 *
 * @since 2019-02-01
 */
public class ServerConsoleHandler extends StreamHandler {

    private ServerConsoleHandler(final Builder builder) {
        setOutputStream(builder.stream);
        setFilter(builder.filter);
        if (builder.formatter != null) {
            setFormatter(builder.formatter);
        }
    }

    /**
     * Returns a new fluent builder for this handler.
     *
     * @return A builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    @Override
    public void close() {
        flush();
    }

    /**
     * A fluent builder for {@link ServerConsoleHandler}.
     */
    public static class Builder implements ConfigurableBuilder<ServerConsoleHandler> {
        /**
         * Root config key.
         */
        public static final String ROOT_KEY = "logging." + ServerConsoleHandler.class.getName();

        /**
         * Stream config key. Selects the output stream from the {@link System} class to write to: {@code "out"} or {@code "err"}.
         * Defaults to {@code "out"}.
         */
        public static final String STREAM_KEY = ROOT_KEY + ".stream";

        /**
         * Level config key. Defaults to {@link Level#ALL}.
         */
        public static final String LEVEL_KEY = ROOT_KEY + ".level";

        /**
         * Filter config key.
         */
        public static final String FILTER_KEY = ROOT_KEY + ".filter";

        /**
         * Formatter config key. Defaults to {@link ServerFormatter}.
         */
        public static final String FORMATTER_KEY = ROOT_KEY + ".formatter";

        /**
         * Formatter config key. Defaults to {@link ServerFormatter}.
         */
        public static final String ENCODER_KEY = ROOT_KEY + ".encoder";

        private PrintStream stream;
        private Level level;
        private Filter filter;
        private Formatter formatter;
        private String encoding;

        private Builder() {
            stream = System.out;
            level = Level.ALL;
        }

        /**
         * Load all properties for this formatter from configuration.
         * Supported keys:
         * <ul>
         * <li>io.helidon.common.logging.ServerConsoleHandler.stream</li>
         * <li>io.helidon.common.logging.ServerConsoleHandler.level</li>
         * <li>io.helidon.common.logging.ServerConsoleHandler.filter</li>
         * <li>io.helidon.common.logging.ServerConsoleHandler.formatter</li>
         * <li>io.helidon.common.logging.ServerConsoleHandler.encoding</li>
         * </ul>
         *
         * @param rootConfig The config instance.
         * @return The updated builder instance, for chaining.
         */
        @Override
        public Builder config(final Config rootConfig) {
            rootConfig.get(STREAM_KEY).asString().ifPresent(this::stream);
            rootConfig.get(LEVEL_KEY).asString().ifPresent(this::level);
            rootConfig.get(FILTER_KEY).asString().ifPresent(this::filter);
            rootConfig.get(FORMATTER_KEY).asString().ifPresent(this::formatter);
            return this;
        }

        /**
         * Sets the output stream to either {@link System#out} or {@link System#err}
         *
         * @param systemStreamName Either "out" or "err".
         * @return The updated builder instance, for chaining.
         */
        public Builder stream(final String systemStreamName) {
            switch (systemStreamName) {
                case "out":
                    return stream(System.out);
                case "err":
                    return stream(System.err);
                default:
                    throw new IllegalArgumentException("unknown system stream: " + systemStreamName);
            }
        }

        /**
         * Sets the output stream to log to.
         *
         * @param stream The stream.
         * @return The updated builder instance, for chaining.
         */
        public Builder stream(final PrintStream stream) {
            this.stream = stream;
            return this;
        }

        /**
         * Sets the level.
         *
         * @param levelName The level name. See {@link Log#toLevel(String)}.
         * @return The updated builder instance, for chaining.
         */
        public Builder level(final String levelName) {
            return level(Log.toLevel(levelName));
        }

        /**
         * Sets the level.
         *
         * @param level The level.
         * @return The updated builder instance, for chaining.
         */
        public Builder level(final Level level) {
            this.level = level;
            return this;
        }

        /**
         * Sets the filter.
         *
         * @param filterClassName The filter class name.
         * @return The updated builder instance, for chaining.
         */
        public Builder filter(final String filterClassName) {
            try {
                return filter((Filter) Class.forName(filterClassName).newInstance());
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Sets the filter.
         *
         * @param filter The filter.
         * @return The updated builder instance, for chaining.
         */
        public Builder filter(final Filter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets the formatter.
         *
         * @param formatterClassName The formatter class name.
         * @return The updated builder instance, for chaining.
         */
        public Builder formatter(final String formatterClassName) {
            try {
                return formatter((Formatter) Class.forName(formatterClassName).newInstance());
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Sets the formatter.
         *
         * @param formatter The filter.
         * @return The updated builder instance, for chaining.
         */
        public Builder formatter(final Formatter formatter) {
            this.formatter = formatter;
            return this;
        }

        @Override
        public ServerConsoleHandler build() {
            return new ServerConsoleHandler(this);
        }
    }
}
