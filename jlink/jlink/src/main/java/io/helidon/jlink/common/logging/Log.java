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

package io.helidon.jlink.common.logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Logging utilities.
 */
public class Log {
    private static final AtomicBoolean LOGGING_INITIALIZED = new AtomicBoolean();
    private static final Map<String, Log> INSTANCES = new HashMap<>();
    private final Logger log;

    private Log(String name) {
        this.log = Logger.getLogger(name);
    }

    /**
     * Returns the named instance.
     *
     * @param name The name.
     * @return The instance.
     */
    public static synchronized Log getLog(final String name) {
        initLogging();
        return INSTANCES.computeIfAbsent(name, key -> new Log(name));
    }

    /**
     * Log a message at INFO level.
     *
     * @param message The message.
     * @param args The message args.
     */
    public void info(final String message, final Object... args) {
        log(Level.INFO, message, args);
    }

    /**
     * Log a message at WARNING level.
     *
     * @param message The message.
     * @param args The message args.
     */
    public void warn(final String message, final Object... args) {
        log(Level.WARNING, message, args);
    }

    /**
     * Log a message at FINE level.
     *
     * @param message The message.
     * @param args The message args.
     */
    public void debug(final String message, final Object... args) {
        log(Level.FINE, message, args);
    }

    /**
     * Log a message at the given level.
     *
     * @param level The level.
     * @param message The message.
     * @param args The message args.
     */
    public void log(final Level level, final String message, final Object... args) {
        if (log.isLoggable(level)) {
            log.log(level, format(message, args));
        }
    }

    /**
     * Returns a description of this JVM.
     *
     * @return The description.
     */
    public static String describeJvm() {
        final Runtime runtime = Runtime.getRuntime();
        final String jvmName = System.getProperty("java.vm.name");
        final String runtimeVersion = System.getProperty("java.runtime.version");
        final int cpus = runtime.availableProcessors();
        final String maxMem = describeSize(runtime.maxMemory());
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        return jvmName + " " + runtimeVersion + " (" + osName + " " + osVersion + "): " + cpus + " cpus, " + maxMem + " max mem";
    }

    /**
     * Returns a description of the given byte count.
     *
     * @param bytes The count, in bytes.
     * @return The description.
     */
    public static String describeSize(final long bytes) {
        final int unit = 1024;
        if (bytes < unit) return bytes + " B";
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final char prefix = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(unit, exp), prefix);
    }

    private static String format(final String message, final Object... args) {
        return args.length == 0 ? message : String.format(message, args);
    }

    private static void initLogging() {
        if (!LOGGING_INITIALIZED.getAndSet(true)) {
            try (InputStream loggingConfig = Log.class.getResourceAsStream("/logging.properties")) {
                if (loggingConfig != null) {
                    LogManager.getLogManager().readConfiguration(loggingConfig);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
