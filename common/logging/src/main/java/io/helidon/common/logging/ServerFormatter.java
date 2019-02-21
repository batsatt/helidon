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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import io.helidon.common.logging.ServerFormatter.Builder.QualifiedNameTransform;
import io.helidon.config.Config;

/**
 * An opinionated and minimally configurable log formatter that includes thread names.
 *
 * @since 2019-01-29
 */
public class ServerFormatter extends Formatter {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private final QualifiedNameTransform qualifiedLogNameTransform;
    private final QualifiedNameTransform qualifiedThreadNameTransform;
    private final DateTimeFormatter dateTimeFormatter;

    private ServerFormatter(final Builder builder) {
        this.qualifiedLogNameTransform = builder.qualifiedLogNameTransform;
        this.qualifiedThreadNameTransform = builder.qualifiedThreadNameTransform;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(builder.dateTimePattern)
                                                  .withZone(ZoneId.of(builder.timeZoneId));
    }

    /**
     * Returns a new fluent builder to build the formatter.
     *
     * @return A builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String format(final LogRecord record) {
        final String message = record.getMessage();
        if (message != null && !message.isEmpty()) {
            if (!message.startsWith("SLF4J")) {
                final StringBuilder sb = new StringBuilder(256);
                appendDateTime(record.getMillis(), sb).append(' ');
                sb.append(record.getLevel().getLocalizedName()).append(' ');
                appendName(record.getLoggerName(), qualifiedLogNameTransform, sb).append(' ');
                appendName(Thread.currentThread().getName(), qualifiedThreadNameTransform, sb).append(' ');
                sb.append(formatMessage(record)).append(' ');
                appendStack(record.getThrown(), sb);
                sb.append(LINE_SEPARATOR);
                return sb.toString();
            } else {
                return "";
            }
        } else {
            return LINE_SEPARATOR;
        }
    }

    /**
     * Append the formatted time.
     *
     * @param millis The time stamp in milliseconds.
     * @param sb The builder to append to.
     * @return The builder, for chaining.
     */
    protected StringBuilder appendDateTime(final long millis, final StringBuilder sb) {
        return sb.append(dateTimeFormatter.format(Instant.ofEpochMilli(millis)));
    }

    /**
     * Append the stack trace if there is an exception.
     *
     * @param thrown The log record or {@code null} if none.
     * @param sb The builder to append to.
     * @return The builder, for chaining.
     */
    protected StringBuilder appendStack(final Throwable thrown, final StringBuilder sb) {
        if (thrown != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                thrown.printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            } catch (Exception ignored) {
            }
        }
        return sb;
    }

    /**
     * Append a name.
     *
     * @param name The name.
     * @param format The name format to apply.
     * @param sb The builder to append to.
     * @return The builder, for chaining.
     */
    protected StringBuilder appendName(final String name, final QualifiedNameTransform format, final StringBuilder sb) {
        sb.append('[');
        if (name == null) {
            sb.append("unknown");
        } else {
            format.append(name, sb);
        }
        sb.append(']');
        return sb;
    }

    /**
     * A fluent builder for {@link ServerFormatter}.
     */
    public static final class Builder implements ConfigurableBuilder<ServerFormatter> {
        /**
         * Root config key.
         */
        public static final String ROOT_KEY = "logging";

        /**
         * Date-Time pattern config key.
         */
        public static final String DATE_TIME_PATTERN_KEY = ROOT_KEY + ".date-time-pattern";

        /**
         * Time zone config key.
         */
        public static final String TIME_ZONE_ID_KEY = ROOT_KEY + ".time-zone-id";

        /**
         * Qualified log name transform config key.
         */
        public static final String QUALIFIED_LOG_NAME_TRANSFORM_KEY = ROOT_KEY + ".qualified-log-name-transform";

        /**
         * Qualified thread name transform config key.
         */
        public static final String QUALIFIED_THREAD_NAME_TRANSFORM_KEY = ROOT_KEY + ".qualified-thread-name-transform";

        /**
         * The default date-time pattern.
         */
        public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

        private static final String DEFAULT_TIME_ZONE_ID = TimeZone.getDefault().getID();

        private String dateTimePattern = DEFAULT_DATE_TIME_PATTERN;
        private String timeZoneId = DEFAULT_TIME_ZONE_ID;
        private QualifiedNameTransform qualifiedLogNameTransform = QualifiedNameTransform.None;
        private QualifiedNameTransform qualifiedThreadNameTransform = QualifiedNameTransform.None;

        /**
         * Transforms for '.' qualified names, e.g. {@code "com.acme.Dynamite"} remains {@code "com.acme.Dynamite"}.
         */
        public enum QualifiedNameTransform {
            /**
             * Names are unchanged.
             */
            None {
                @Override
                public StringBuilder append(final String name, final StringBuilder sb) {
                    return sb.append(name);
                }
            },

            /**
             * Qualifier is minimized to only the first character after each '.', e.g. {@code "com.acme.Dynamite"}
             * becomes {@code "c.a.Dynamite"}.
             */
            Minimize {
                @Override
                public StringBuilder append(final String name, final StringBuilder sb) {
                    int index = 0;
                    while (true) {
                        final int nextDot = name.indexOf('.', index);
                        if (nextDot > 0) {
                            sb.append(name.charAt(index)).append('.');
                            index = nextDot + 1;
                        } else {
                            sb.append(name.substring(index));
                            break;
                        }
                    }
                    return sb;
                }
            },

            /**
             * Qualifiers are stripped, e.g. {@code "com.acme.Dynamite"} becomes {@code "Dynamite"}.
             */
            Strip {
                @Override
                public StringBuilder append(final String name, final StringBuilder sb) {
                    final int lastDot = name.lastIndexOf('.');
                    return sb.append(lastDot < 0 ? name : name.substring(lastDot + 1));
                }
            };

            /**
             * Append the formatted name.
             *
             * @param name The name.
             * @param sb The builder to append to.
             * @return The builder, for chaining.
             */
            public abstract StringBuilder append(final String name, final StringBuilder sb);
        }

        @Override
        public ServerFormatter build() {
            return new ServerFormatter(this);
        }

        /**
         * Load all properties for this formatter from configuration.
         * Supported keys:
         * <ul>
         * <li>{@link #DATE_TIME_PATTERN_KEY}</li>
         * <li>{@link #TIME_ZONE_ID_KEY}</li>
         * <li>{@link #QUALIFIED_LOG_NAME_TRANSFORM_KEY}</li>
         * <li>{@link #QUALIFIED_THREAD_NAME_TRANSFORM_KEY}</li>
         * </ul>
         *
         * @param rootConfig Root config.
         * @return The updated builder instance, for chaining.
         */
        @Override
        public Builder config(final Config rootConfig) {
            rootConfig.get(DATE_TIME_PATTERN_KEY).asString().ifPresent(this::dateTimePattern);
            rootConfig.get(TIME_ZONE_ID_KEY).asString().ifPresent(this::timeZoneId);
            rootConfig.get(QUALIFIED_LOG_NAME_TRANSFORM_KEY).asString().ifPresent(this::qualifiedLogNameTransform);
            rootConfig.get(QUALIFIED_THREAD_NAME_TRANSFORM_KEY).asString().ifPresent(this::qualifiedThreadNameTransform);
            return this;
        }

        /**
         * Sets the date-time pattern.
         *
         * @param dateTimePattern The pattern; see {@link DateTimeFormatter#ofPattern(String)}.
         * @return The updated builder instance, for chaining.
         */
        public Builder dateTimePattern(final String dateTimePattern) {
            this.dateTimePattern = dateTimePattern;
            return this;
        }

        /**
         * Sets the time zone id.
         *
         * @param timeZoneId The id; see {@link TimeZone#getTimeZone(String)}.
         * @return The updated builder instance, for chaining.
         */
        public Builder timeZoneId(final String timeZoneId) {
            this.timeZoneId = timeZoneId;
            return this;
        }

        /**
         * Sets the log name qualifier transform.
         *
         * @param qualifiedLogNameTransform The format; see {@link QualifiedNameTransform}.
         * @return The updated builder instance, for chaining.
         */
        public Builder qualifiedLogNameTransform(final String qualifiedLogNameTransform) {
            return qualifiedLogNameTransform(QualifiedNameTransform.valueOf(qualifiedLogNameTransform));
        }

        /**
         * Sets the log name qualifier format.
         *
         * @param qualifiedLogNameTransform The format; see {@link QualifiedNameTransform}.
         * @return The updated builder instance, for chaining.
         */
        public Builder qualifiedLogNameTransform(final QualifiedNameTransform qualifiedLogNameTransform) {
            this.qualifiedLogNameTransform = qualifiedLogNameTransform;
            return this;
        }

        /**
         * Sets the thread name qualifier format.
         *
         * @param qualifiedThreadNameTransform The format; see {@link QualifiedNameTransform}.
         * @return The updated builder instance, for chaining.
         */
        public Builder qualifiedThreadNameTransform(final String qualifiedThreadNameTransform) {
            return qualifiedThreadNameTransform(QualifiedNameTransform.valueOf(qualifiedThreadNameTransform));
        }

        /**
         * Sets the thread name qualifier format.
         *
         * @param qualifiedThreadNameTransform The format; see {@link QualifiedNameTransform}.
         * @return The updated builder instance, for chaining.
         */
        public Builder qualifiedThreadNameTransform(final QualifiedNameTransform qualifiedThreadNameTransform) {
            this.qualifiedThreadNameTransform = qualifiedThreadNameTransform;
            return this;
        }
    }
}
