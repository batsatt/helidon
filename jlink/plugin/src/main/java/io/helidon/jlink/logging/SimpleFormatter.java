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

package io.helidon.jlink.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A simple log message formatter.
 */
public class SimpleFormatter extends Formatter {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String DEFAULT_TIME_ZONE_ID = TimeZone.getDefault().getID();

    private final DateTimeFormatter dateTimeFormatter;

    /**
     * Constructor.
     */
    public SimpleFormatter() {
        this(DEFAULT_DATE_TIME_PATTERN, DEFAULT_TIME_ZONE_ID);
    }

    /**
     * Constructor.
     *
     * @param dateTimePattern The date time pattern.
     * @param timeZoneId The time zone id.
     */
    public SimpleFormatter(String dateTimePattern, String timeZoneId) {
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern)
                                                  .withZone(ZoneId.of(timeZoneId));
    }

    @Override
    public String format(final LogRecord record) {
        final String message = record.getMessage();
        if (message != null && !message.isEmpty()) {
            if (!message.startsWith("SLF4J")) {
                final StringBuilder sb = new StringBuilder(256);
                //appendDateTime(record.getMillis(), sb).append(' ');
                if (record.getLevel().intValue() > Level.INFO.intValue()) {
                    sb.append(record.getLevel().getLocalizedName()).append(": ");
                }
                //appendName(record.getLoggerName(), sb).append(' ');
                // appendName(Thread.currentThread().getName(), sb).append(' ');
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
    private StringBuilder appendDateTime(final long millis, final StringBuilder sb) {
        return sb.append(dateTimeFormatter.format(Instant.ofEpochMilli(millis)));
    }

    /**
     * Append the stack trace if there is an exception.
     *
     * @param thrown The log record or {@code null} if none.
     * @param sb The builder to append to.
     * @return The builder, for chaining.
     */
    private StringBuilder appendStack(final Throwable thrown, final StringBuilder sb) {
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
     * @param sb The builder to append to.
     * @return The builder, for chaining.
     */
    private StringBuilder appendName(final String name, final StringBuilder sb) {
        return sb.append('[')
                 .append(name == null ? "unknown" : name)
                 .append(']');
    }
}
