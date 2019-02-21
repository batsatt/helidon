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

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.MemoryHandler;
import java.util.logging.SocketHandler;
import java.util.stream.Collectors;

import io.helidon.config.Config;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;

/**
 * Logging utilities supporting java.util.logging:
 * <ol>
 * <li>A fluent builder for {@link LogManager} configuration that:
 * <ul>
 * <li>Uses {@link Config} as an alternative to a {@code logging.properties} file. In addition to consolidating configuration,
 * this enables overrides via system properties or environment variables.</li>
 * <li>Replaces the {@code System.out} and {@code System.err} streams with streams that write to {@code "system.out"} and
 * {@code "system.err"} loggers, enabling consistent formatting and output control via levels and handlers.</li>
 * <li>Changes the default handler to a console handler that writes to {@link System#out} instead of {@link System#err}; see
 * {@link ServerConsoleHandler}).</li>
 * <li>Changes the default formatter for all handlers with an opinionated format that includes thread names; see
 * {@link ServerFormatter}.</li>
 * </ul>
 * </li>
 * <li>{@link Log} wrappers for {@link Logger} instances, providing additional convenience:
 * <ul>
 * <li>Methods supporting the much richer {@link String#format(String, Object...)} substitution instead of the older {@code '{n}'}
 * string only substitution.</li>
 * <li>Supports case insensitive log level names, including 'warn' and 'debug' as aliases for {@link Level#WARNING} and
 * {@link Level#FINEST}.</li>
 * </ul>
 * </li>
 * <li>Enables new {@link Handler} implementations to be configurable via {@link Config}, see {@link ConfigurableBuilder} and
 * {@link SimpleFileHandler} as an example.</li>
 *
 * </ol>
 *
 *
 *
 *
 * <h2>Configuration Properties</h2>
 *
 * <table cellspacing="0" cellpadding="5">
 *
 * <tr>
 * <td class="tab">Property</td>
 * <td class="tab">Description</td>
 * </tr>
 *
 * <tr>
 * <td><code>handlers</code></td>
 * <td>A white space or comma separated list of handler class names to be added to the root {@link Logger}, e.g.
 * {@link ConsoleHandler}, {@link ServerConsoleHandler}, {@link FileHandler}, {@link SimpleFileHandler}, {@link SocketHandler},
 * {@link MemoryHandler}. Defaults to {@link ServerConsoleHandler}.
 * <p>
 * Note that if no explicit defaultFormatter property is declared for a specified handler, the {@link ServerFormatter} is used rather
 * than the handler's normal default.</p></td>
 * </tr>
 * <tr>
 * <td><code>config</code></td>
 * <td>A white space or comma separated list of class names that will be instantiated when the LogManager is initialized.
 * The constructors of these classes can execute arbitrary configuration code.</td>
 * </tr>
 * <tr>
 * <td><code>&lt;logName&gt;.handlers</code></td>
 * <td>Sets the handler classes to use for a given {@link Logger} in the hierarchy. Replace &lt;logName&gt; with
 * a specific name of a {@link Logger}, e.g. {@code com.acme.Dynamite.level}.</td>
 * </tr>
 * <tr>
 * <td><code>&lt;logName&gt;.useParentHandlers</code></td>
 * <td>Tells a given {@link Logger} whether it should log to its parents or not (<code>true</code> or
 * <code>false</code>). Defaults to {@code true}</td>
 * </tr>
 * <tr>
 * <td><code>&lt;logName&gt;.level</code></td>
 * <td>Tells a given {@link Logger} what minimum log level it should log.
 * </p>
 * Note that {@code ".level"} is valid and sets the root logger's level, which then acts as a global default; unless specified,
 * defaults to {@link Level#INFO}.</td>
 * </tr>
 * <tr>
 * <td><code>&nbsp;</code></td>
 * <td></td>
 * </tr>
 * </table>
 *
 * @since 2019-01-29
 */
public class Logging {

    /**
     * The original {@link System#out} stream.
     */
    public static final PrintStream SYSTEM_OUT = System.out;

    /**
     * The original {@link System#out} stream.
     */
    public static final PrintStream SYSTEM_ERR = System.err;

    /**
     * The name of the log that writes to the {@link System#out} stream.
     */
    public static final String SYSTEM_OUT_LOG_NAME = "system.out";

    /**
     * The name of the log that writes to the {@link System#out} stream.
     */
    public static final String SYSTEM_ERR_LOG_NAME = "system.err";

    /**
     * Returns a new configuration builder.
     *
     * @return The builder.
     */
    public static ConfigurationBuilder builder() {
        return new ConfigurationBuilder();
    }

    /**
     * Get a {@link Log} instance with the given name.
     * Ensures configuration has occurred via {@link #ensureConfigured()}.
     *
     * @param name Logger name.
     * @return The instance.
     */
    public static Log getLog(final String name) {
        ensureConfigured();
        return new Log(getLogger(name));
    }

    /**
     * Get a {@link Log} instance with the given name and resource bundle name.
     * Ensures configuration has occurred via {@link #ensureConfigured()}.
     *
     * @param name Logger name.
     * @param resourceBundleName Resource bundle name to be used for localizing messages.
     * @return The instance.
     */
    public static Log getLog(final String name, final String resourceBundleName) {
        ensureConfigured();
        return new Log(getLogger(name, resourceBundleName));
    }

    /**
     * Ensure a reasonable default configuration if not already configured.
     */
    public static void ensureConfigured() {
        if (!ConfigurationBuilder.CONFIGURED.get()) {
            synchronized (ConfigurationBuilder.CONFIGURED) {
                if (!ConfigurationBuilder.CONFIGURED.get()) {
                    Logging.builder().apply();
                }
            }
        }
    }

    /**
     * Fluent logging configuration builder.
     */
    public static final class ConfigurationBuilder {
        /**
         * Root config key.
         */
        public static final String ROOT_KEY = "logging";

        /**
         * Handler class names config key. See {@link LogManager}.
         */
        public static final String HANDLERS_KEY = ROOT_KEY + ".handlers";

        /**
         * Config class names config key. See {@link LogManager}.
         */
        public static final String CONFIG_KEY = ROOT_KEY + ".config";

        /**
         * System out print level config key. Defaults to {@link Level#INFO}.
         */
        public static final String SYSTEM_OUT_PRINT_LEVEL_KEY = ROOT_KEY + ".system-out-print-level";

        /**
         * System err print level config key. Defaults to {@link Level#INFO}.
         */
        public static final String SYSTEM_ERR_PRINT_LEVEL_KEY = ROOT_KEY + ".system-err-print-level";

        private static final AtomicBoolean CONFIGURED = new AtomicBoolean();
        private static final String LINE_SEPARATOR = System.getProperty("line.separator");
        private static final Level DEFAULT_LEVEL = Level.INFO;
        private static final String JDK_LOGGING_PACKAGE = "java.util.logging";
        private static final String SLF4J_LOGGER_FACTORY_CLASS = "org.slf4j.LoggerFactory";
        private static final String SLF4J_LOGGER_FACTORY_METHOD = "getILoggerFactory";
        private static final String SLF4J_JDK_FACTORY_PREFIX = "JDK";
        private static final String ROOT_LOGGER_NAME = "";
        private static final String HANDLERS_LEAF_KEY = "handlers";
        private static final String CONFIG_LEAF_KEY = "config";
        private static final String LEVEL_KEY = "level";
        private static final String FORMATTER_KEY = "formatter";
        private static final String USE_PARENT_HANDLERS_KEY = "useParentHandlers";
        private static final String CLOSE_ON_RESET_KEY = "ensureCloseOnReset";
        private static final String PATCH_QUALIFIER = ".patch";

        private final Map<String, List<String>> handlerClassNamesByLogName;
        private final Set<String> handlerClassNames;
        private final Set<String> handlerClassNamesWithFormatter;
        private final Map<String, String> loggingProperties;
        private final List<String> configClassNames;
        private final Map<String, Handler> handlerCache;

        private Config config;
        private Level systemOutPrintLevel;
        private Level systemErrPrintLevel;
        private Formatter defaultFormatter;

        private ConfigurationBuilder() {
            systemOutPrintLevel = DEFAULT_LEVEL;
            systemErrPrintLevel = DEFAULT_LEVEL;
            configClassNames = new ArrayList<>();
            handlerClassNamesByLogName = new HashMap<>();
            handlerClassNames = new HashSet<>();
            handlerClassNamesWithFormatter = new HashSet<>();
            loggingProperties = new LinkedHashMap<>();
            handlerCache = new HashMap<>();
            logLevel(ROOT_LOGGER_NAME, DEFAULT_LEVEL);
        }

        /**
         * Load all properties for this builder from configuration under the {@link "logging"} key.
         * Supported keys:
         * <ul>
         * <li>All properties supported by {@link LogManager}</li>
         * <li>{@link #SYSTEM_OUT_PRINT_LEVEL_KEY}</li>
         * <li>{@link #SYSTEM_ERR_PRINT_LEVEL_KEY}</li>
         * </ul>
         *
         * @param config config.
         * @return The updated builder instance, for chaining.
         */
        public ConfigurationBuilder config(final Config config) {
            this.config = config;
            final Config loggingConfig = config.get(ROOT_KEY).detach();
            if (loggingConfig.exists()) {

                // Note that we must end up mapping config for any java.util.logging handler into logging.properties that we pass
                // to the LogManager: these handlers configure themselves by calling back into the LogManager to fetch properties.
                // For all other handlers, we assume that they implement a builder() method that returns ConfigurableBuilder; it
                // is an error if they do not.

                config.get(HANDLERS_KEY).asString().ifPresent(handlerClassName -> addHandler(ROOT_LOGGER_NAME, handlerClassName));
                config.get(CONFIG_KEY).asString().ifPresent(this::addConfigClass);
                config.get(SYSTEM_OUT_PRINT_LEVEL_KEY).asString().ifPresent(this::systemOutPrintLevel);
                config.get(SYSTEM_ERR_PRINT_LEVEL_KEY).asString().ifPresent(this::systemErrPrintLevel);

                // Add all logger config to our logging properties, remembering any handler declarations. Entries are ignored
                // unless they end with a known logger property: level, handlers, useParentHandlers, ensureCloseOnReset.

                addLoggerConfig(loggingConfig);

                // Add all JDK handler config to our logging properties

                addJdkHandlerConfig(loggingConfig);
            }
            return this;
        }

        /**
         * Add a handler for a given log.
         *
         * @param logName The log name
         * @param handlerClassName The handler class name(s). May be a whitespace or comma separated list.
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder addHandler(final String logName, final String handlerClassName) {
            split(handlerClassName).forEach(className -> {
                handlerClassNames.add(handlerClassName);
                handlerClassNamesByLogName.computeIfAbsent(logName, n -> new ArrayList<>()).add(className);
            });
            return this;
        }

        /**
         * Add a configuration class that will be instantiated by the LogManager and can execute arbitrary initialization.
         *
         * @param configClassName The config class name(s). May be a whitespace or comma separated list.
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder addConfigClass(final String configClassName) {
            configClassNames.addAll(split(configClassName));
            return this;
        }

        /**
         * Sets the log level for the given logger name.
         *
         * @param logName The logger name.
         * @param levelName The level name (see {@link Log#toLevel(String)}).
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder logLevel(final String logName, final String levelName) {
            return (logLevel(logName, Log.toLevel(levelName)));
        }

        /**
         * Sets the root log level that will be used by default for all loggers.
         *
         * @param logLevel The level.
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder logLevel(final String logName, final Level logLevel) {
            loggingProperties.put(logName + "." + LEVEL_KEY, logLevel.toString());
            return this;
        }

        /**
         * Sets the log level at which {@link System#out} printed messages will be logged.
         *
         * @param levelName The level name (see {@link Log#toLevel(String)}).
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder systemOutPrintLevel(final String levelName) {
            return systemOutPrintLevel(Log.toLevel(levelName));
        }

        /**
         * Sets the log level at which {@link System#out} printed messages will be logged.
         *
         * @param printLevel The level.
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder systemOutPrintLevel(final Level printLevel) {
            this.systemOutPrintLevel = printLevel;
            return this;
        }

        /**
         * Sets the log level at which {@link System#err} printed messages will be logged.
         *
         * @param levelName The level name (see {@link Log#toLevel(String)}).
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder systemErrPrintLevel(final String levelName) {
            return systemErrPrintLevel(Log.toLevel(levelName));
        }

        /**
         * Sets the log level at which {@link System#err} printed messages will be logged.
         *
         * @param printLevel The level.
         * @return This instance, for chaining.
         */
        public ConfigurationBuilder systemErrPrintLevel(final Level printLevel) {
            this.systemErrPrintLevel = printLevel;
            return this;
        }

        /**
         * Applies the configuration.
         */
        public void apply() {
            synchronized (CONFIGURED) {
                final Logger rootLogger = initializeAndGetRootLogger();

                // Configure handlers

                if (handlerClassNamesByLogName.isEmpty()) {

                    // None specified, so set our default

                    setDefaultHandler(rootLogger);

                } else {

                    // Add any configurable handlers and reset the default formatter for
                    // any handler which did not specify one

                    handlerClassNamesByLogName.keySet().forEach(this::configureHandlers);
                }

                // Redirect system out and error to a logger

                try {
                    System.setOut(new LoggingPrintStream(getLogger(SYSTEM_OUT_LOG_NAME), systemOutPrintLevel));
                    System.setErr(new LoggingPrintStream(getLogger(SYSTEM_ERR_LOG_NAME), systemErrPrintLevel));
                } catch (Exception e) {
                    SYSTEM_ERR.println("WARNING: Unable to reset system out and err: " + e);
                }

                CONFIGURED.set(true);
            }
        }

        private void setDefaultHandler(final Logger rootLogger) {
            final Handler handler = ServerConsoleHandler.builder().config(config).build();
            handler.setFormatter(defaultFormatter());
            handler.setLevel(Level.ALL);  // Don't filter at the handler level
            rootLogger.addHandler(handler);

            // Remove any handlers that were added automatically

            for (final Handler h : rootLogger.getHandlers()) {
                if (!h.equals(handler)) {
                    rootLogger.removeHandler(h);
                }
            }
        }

        private void configureHandlers(final String logName) {
            final Logger logger = Logger.getLogger(logName);
            final Handler[] existing = logger.getHandlers();
            handlerClassNamesByLogName.get(logName).forEach(className -> {
                final Handler jdkHandler = jdkHandler(className, existing);
                if (jdkHandler != null) {
                    if (!handlerClassNamesWithFormatter.contains(className)) {
                        // No explicit formatter was set, so use our default
                        jdkHandler.setFormatter(defaultFormatter());
                    }
                } else {
                    final Handler handler = handlerCache.computeIfAbsent(className, n -> newConfigurableHandler(className));
                    logger.addHandler(handler);
                }
            });
        }

        private static Handler jdkHandler(final String handlerClassName, final Handler[] handlers) {
            for (final Handler handler : handlers) {
                if (handler.getClass().getName().equals(handlerClassName)) {
                    return handler;
                }
            }
            return null;
        }

        private void addLoggerConfig(final Config loggingConfig) {
            loggingConfig.traverse()
                         .filter(Config::isLeaf)
                         .forEach(node -> {
                             final String key = node.key().name();
                             final String qualifiedKey = node.key().toString();
                             final String value = node.asString().get();
                             final String parentQualifiedKey = node.key().parent().toString();
                             switch (key) {
                                 case HANDLERS_LEAF_KEY: {
                                     if (!parentQualifiedKey.equals(ROOT_LOGGER_NAME)) { // Already processed
                                         addHandler(parentQualifiedKey, value);
                                         loggingProperties.put(qualifiedKey, value);
                                     }
                                     break;
                                 }

                                 case USE_PARENT_HANDLERS_KEY: {
                                     loggingProperties.put(qualifiedKey, value);
                                     break;
                                 }

                                 case CLOSE_ON_RESET_KEY: {
                                     if (qualifiedKey.endsWith("." + HANDLERS_LEAF_KEY)) {
                                         loggingProperties.put(qualifiedKey, value);
                                     }
                                 }

                                 case LEVEL_KEY: {
                                     // Ignore any key that starts with a non-jdk handler class name, as well
                                     // as any system property that ends in "patch.level"
                                     if (!isNonJdkHandlerClass(parentQualifiedKey) && !qualifiedKey.endsWith(PATCH_QUALIFIER)) {
                                         loggingProperties.put(qualifiedKey, Log.toLevel(value).toString());
                                     }
                                 }
                             }
                         });
        }

        private boolean isNonJdkHandlerClass(final String className) {
            return handlerClassNames.contains(className) && !className.startsWith(JDK_LOGGING_PACKAGE);
        }

        private void addJdkHandlerConfig(final Config loggingConfig) {
            handlerClassNames.forEach(name -> {
                if (name.startsWith(JDK_LOGGING_PACKAGE)) {
                    final Config handlerConfig = loggingConfig.get(name);
                    handlerConfig.traverse()
                                 .forEach(node -> {
                                     final String key = node.key().name();
                                     final String value = node.asString().get();
                                     loggingProperties.put(key, value);
                                     if (value.equals(FORMATTER_KEY)) {
                                         handlerClassNamesWithFormatter.add(key);
                                     }
                                 });
                }
            });
        }

        private Handler newConfigurableHandler(final String className) {
            final ConfigurableBuilder builder = ConfigurableBuilder.newBuilder(className);
            final Class<?> handlerClass = builder.getClass().getEnclosingClass();
            if (Handler.class.isAssignableFrom(handlerClass)) {
                return (Handler) builder.config(config).build();
            } else {
                throw new IllegalArgumentException(className + " is not a Handler");
            }
        }

        private Logger initializeAndGetRootLogger() {
            if (config == null) {
                config(DefaultConfig.config());
            }
            final LogManager logManager = initializeLogManager();
            initializeSLF4J();
            return logManager.getLogger(ROOT_LOGGER_NAME);
        }

        private LogManager initializeLogManager() {
            try {
                final LogManager logManager = LogManager.getLogManager();
                final String loggingProperties = createLoggingProperties();
                logManager.readConfiguration(new ByteArrayInputStream(loggingProperties.getBytes()));
                // TODO: java9 logManager.updateConfiguration(new ByteArrayInputStream(loggingProperties.getBytes()), null);
                return logManager;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String createLoggingProperties() {
            final StringBuilder sb = new StringBuilder(1024);

            // JDK handlers

            addProperty(HANDLERS_LEAF_KEY,
                        handlerClassNames.stream()
                                         .filter(name -> name.startsWith(JDK_LOGGING_PACKAGE))
                                         .collect(Collectors.toList()),
                        sb);
            // Config

            addProperty(CONFIG_LEAF_KEY, configClassNames, sb);

            // All other properties

            loggingProperties.forEach((k, v) -> {
                addProperty(k, v, sb);
            });

            return sb.toString();
        }

        private static void addProperty(final String key, final Collection<String> names, final StringBuilder sb) {
            if (!names.isEmpty()) {
                addProperty(key, String.join(",", names), sb);
            }
        }

        private static void addProperty(final String key, final String value, final StringBuilder sb) {
            sb.append(key).append(" = ").append(value).append(LINE_SEPARATOR);
        }

        private Formatter defaultFormatter() {
            if (defaultFormatter == null) {
                defaultFormatter = ServerFormatter.builder().config(config).build();
            }
            return defaultFormatter;
        }

        private static void initializeSLF4J() {
            try {
                final Class<?> factoryAccessorClass = Class.forName(SLF4J_LOGGER_FACTORY_CLASS);
                final Method factoryAccessor = factoryAccessorClass.getMethod(SLF4J_LOGGER_FACTORY_METHOD);
                final Class<?> factoryClass = factoryAccessor.invoke(null).getClass();
                Class<?> factoryType = factoryClass;
                while (factoryType != Object.class) {
                    if (factoryClass.getSimpleName().startsWith(SLF4J_JDK_FACTORY_PREFIX)) {
                        return; // We're good, SLF4J is present and configured to use java.util.logging.
                    }
                    factoryType = factoryType.getSuperclass();
                }
                throw new IllegalStateException("SLF4J configured with wrong factory: " + factoryClass);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
            }
        }

        private static List<String> split(final String names) {
            final String name = requireNonNull(names).trim();
            if (name.contains(" ")) {
                return asList(name.split(" "));
            } else if (name.contains(",")) {
                return asList(name.split(","));
            } else {
                return singletonList(name);
            }
        }
    }

    private Logging() {
    }
}
