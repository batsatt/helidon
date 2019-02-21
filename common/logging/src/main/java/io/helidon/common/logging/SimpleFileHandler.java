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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import io.helidon.config.Config;

import static java.util.Objects.requireNonNull;

/**
 * A configurable {@link Handler} that writes to a single file and does not rotate.
 *
 * @since 2019-02-01
 */
public class SimpleFileHandler extends Handler {
    private final BufferedWriter out;

    /**
     * Returns a new fluent builder for this handler.
     *
     * @return A builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    SimpleFileHandler(final Builder builder) {
        try {
            this.out = Files.newBufferedWriter(builder.file, Charset.forName(builder.encoding));
            if (builder.level != null) {
                setLevel(builder.level);
            }
            if (builder.formatter != null) {
                setFormatter(builder.formatter);
            }
            if (builder.filter != null) {
                setFilter(builder.filter);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (isLoggable(record)) {
            int errorCode = 0;
            try {
                errorCode = ErrorManager.FORMAT_FAILURE;
                final String msg = getFormatter().format(record);
                errorCode = ErrorManager.WRITE_FAILURE;
                out.write(msg);
                out.flush();
            } catch (Exception e) {
                reportError(null, e, errorCode);
            }
        }
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (Exception ex) {
            reportError(null, ex, ErrorManager.FLUSH_FAILURE);
        }
    }

    @Override
    public void close() throws SecurityException {
        try {
            out.flush();
            out.close();
        } catch (Exception ex) {
            reportError(null, ex, ErrorManager.CLOSE_FAILURE);
        }
    }

    /**
     * A fluent builder for {@link SimpleFileHandler}.
     */
    public static class Builder implements ConfigurableBuilder<SimpleFileHandler> {
        /**
         * Root config key.
         */
        public static final String ROOT_KEY = "logging." + SimpleFileHandler.class.getName();

        /**
         * File path config key.
         */
        public static final String FILE_KEY = ROOT_KEY + ".file";

        /**
         * Level config key.
         */
        public static final String LEVEL_KEY = ROOT_KEY + ".level";

        /**
         * Filter config key.
         */
        public static final String FILTER_KEY = ROOT_KEY + ".filter";

        /**
         * Encoding config key. Defaults to "ISO-8859-1".
         */
        public static final String ENCODING_KEY = ROOT_KEY + ".encoding";

        /**
         * Formatter config key. Defaults to {@link ServerFormatter}.
         */
        public static final String FORMATTER_KEY = ROOT_KEY + ".formatter";

        private static final String DEFAULT_ENCODING = "ISO_8859-1";

        private Path file;
        private Level level;
        private Filter filter;
        private String encoding;
        private Formatter formatter;

        private Builder() {
            encoding = DEFAULT_ENCODING;
        }

        /**
         * Load all properties for this formatter from configuration.
         * Supported keys:
         * <ul>
         * <li>io.helidon.common.logging.SimpleFileHandler.file (required)</li>
         * <li>io.helidon.common.logging.SimpleFileHandler.level</li>
         * <li>io.helidon.common.logging.SimpleFileHandler.filter</li>
         * <li>io.helidon.common.logging.SimpleFileHandler.encoding</li>
         * <li>io.helidon.common.logging.SimpleFileHandler.formatter</li>
         * </ul>
         *
         * @param rootConfig The config instance.
         * @return The updated builder instance, for chaining.
         */
        @Override
        public Builder config(final Config rootConfig) {
            path(rootConfig.get(FILE_KEY).asString().orElseThrow(() -> new IllegalArgumentException(FILE_KEY + " missing")));
            rootConfig.get(LEVEL_KEY).asString().ifPresent(this::level);
            rootConfig.get(FILTER_KEY).asString().ifPresent(this::filter);
            rootConfig.get(ENCODING_KEY).asString().ifPresent(this::encoding);
            rootConfig.get(FORMATTER_KEY).asString().ifPresent(this::formatter);
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

        /**
         * Sets the character encoding.
         *
         * @param encoding The character encoding (a.k.a. "charset").
         * @return The updated builder instance, for chaining.
         */
        public Builder encoding(final String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         * Sets the file system path.
         *
         * @param file The path.
         * @return The updated builder instance, for chaining.
         */
        public Builder path(final String file) {
            return path(Paths.get(file));
        }

        /**
         * Sets the file system path.
         *
         * @param file The path.
         * @return The updated builder instance, for chaining.
         */
        public Builder path(final Path file) {
            if (Files.exists(file)) {
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException(file + " is not a regular file");
                } else if (!Files.isWritable(file)) {
                    throw new IllegalArgumentException(file + " is not writable");
                }
            } else {
                final Path dir = file.getParent();
                if (dir != null && (!Files.exists(dir) || !Files.isWritable(dir))) {
                    throw new IllegalArgumentException(file + " is not in a valid directory");
                }
            }
            this.file = file;
            return this;
        }

        @Override
        public SimpleFileHandler build() {
            requireNonNull(file, "missing required property: " + ROOT_KEY);
            return new SimpleFileHandler(this);
        }
    }
}
