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

package io.helidon.jlink.image.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.spi.ToolProvider;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.ClassDataSharing;
import io.helidon.jlink.common.util.FileUtils;
import io.helidon.jlink.image.modules.plugins.ApplicationContext;
import io.helidon.jlink.image.modules.plugins.BootOrderPlugin;
import io.helidon.jlink.image.modules.plugins.HelidonPlugin;

import jdk.tools.jlink.plugin.PluginException;

/**
 * Create a custom JRE by mapping all jars of a Helidon application into modules and linking
 * them via jlink, then adding a CDS archive.
 */
public class ModulesLinker {

    /**
     * Main entry point.
     *
     * @param args Command line arguments.
     * @throws Exception If an error occurs.
     * @see Configuration.Builder#commandLine(String...)
     */
    public static void main(String... args) throws Exception {
        linker(Configuration.builder()
                            .commandLine(args)
                            .build())
            .link();
    }

    /**
     * Returns a new linker with the given configuration.
     *
     * @param config The configuration.
     * @return The linker.
     */
    public static ModulesLinker linker(Configuration config) {
        return new ModulesLinker(config);
    }

    private static final Log LOG = Log.getLog("linker");
    private static final String WELD_JRT_JAR_PATH = "libs/helidon-weld-jrt.jar";
    private static final String JLINK_DEBUG_PROPERTY = "jlink.debug";
    private final Configuration config;
    private final List<String> jlinkArgs;
    private StringBuilder helidonPluginArgs;
    private final ToolProvider jlink;

    private ModulesLinker(Configuration config) {
        this.config = config;
        this.jlinkArgs = new ArrayList<>();
        this.helidonPluginArgs = new StringBuilder();
        this.jlink = ToolProvider.findFirst("jlink").orElseThrow();
        if (config.verbose()) {
            Log.setAllLevels(Level.FINEST);
            System.setProperty(JLINK_DEBUG_PROPERTY, "true");
        }
    }

    /**
     * Create the JRE.
     *
     * @return The JRE directory.
     */
    public Path link() {
        final long startTime = System.currentTimeMillis();
        buildHelidonPluginArguments();
        buildJlinkArguments();
        buildJre();
        buildCdsArchive();
        complete(startTime);
        return config.jreDirectory();
    }

    private ModulesLinker buildHelidonPluginArguments() {

        // Tell our plugin where the main application module is.
        // NOTE: jlink quirk here, where the first argument cannot be named.

        appendHelidonPluginArg(null, config.mainJar());

        // Tell our plugin where the weld-jrt.jar is

        final Path ourModule = getModulePath(getClass());
        final Path weldJrtModule = FileUtils.assertExists(ourModule.getParent().resolve(WELD_JRT_JAR_PATH));
        appendHelidonPluginArg(HelidonPlugin.WELD_JRT_MODULE_KEY, weldJrtModule);

        // Tell our plugin what JDK to use

        appendHelidonPluginArg(HelidonPlugin.JDK_KEY, config.jdkDirectory());

        // Tell our plugin where the patches live, if provided

        if (config.patchesDirectory() != null) {
            appendHelidonPluginArg(HelidonPlugin.PATCHES_DIR_KEY, config.patchesDirectory());
        }

        return this;
    }

    private ModulesLinker buildJlinkArguments() {

        // Tell jlink to use our plugins

        addArgument("--" + HelidonPlugin.NAME + "=" + helidonPluginArgs.toString());
        addArgument("--" + BootOrderPlugin.NAME);

        // Tell jlink to use our BootModulesPlugin instead of the SystemModulesPlugin

        addArgument("--disable-plugin", "system-modules");
        addArgument("--boot-modules");

        // Tell jlink the directory to create and write the image

        addArgument("--output", config.jreDirectory());

        // Since we expect Helidon and dependencies to leverage automatic modules, we
        // can't configure jlink with the app and libs directly. It requires some module,
        // however, so just add a place holder; we won't add it to the image.

        addArgument("--add-modules", "java.logging");

        // Tell jlink to resolve and include the service providers

        addArgument("--bind-services");

        // Tell jlink to strip out unnecessary stuff

        if (config.stripDebug()) {
            addArgument("--strip-debug");
        }
        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        addArgument("--compress", "2");

        return this;
    }

    private void buildJre() {
        final int result = jlink.run(System.out, System.err, jlinkArgs.toArray(new String[0]));
        if (result != 0) {
            throw new Error("JRE creation failed.");
        }
        LOG.info("JRE created: %s", config.jreDirectory());
    }

    private void buildCdsArchive() {
        if (config.cds()) {
            try {
                final ApplicationContext context = ApplicationContext.get();
                final ClassDataSharing cds = ClassDataSharing.builder()
                                                             .jre(config.jreDirectory())
                                                             .moduleName(context.applicationModuleName())
                                                             .showOutput(config.verbose())
                                                             .build();
                LOG.info("Added CDS archive %s", cds.archiveFile());
            } catch (Exception e) {
                throw new PluginException(e);
            }
        }
    }

    private ModulesLinker complete(long startTime) {
        final long elapsed = System.currentTimeMillis() - startTime;
        final float startSeconds = elapsed / 1000F;
        LOG.info("JRE completed in %.1f seconds: %s", startSeconds, config.jreDirectory());
        return this;
    }

    private void appendHelidonPluginArg(String key, Path value) {
        appendPluginArg(key, value, helidonPluginArgs);
    }

    private void appendPluginArg(String key, Path value, StringBuilder args) {
        if (args.length() > 0) {
            args.append(':');
        }
        if (key != null) {
            args.append(key).append('=');
        }
        args.append(value);
    }

    private static Path getModulePath(Class<?> moduleClass) {
        return Paths.get(moduleClass.getProtectionDomain().getCodeSource().getLocation().getPath());
    }

    private void addArgument(String argument) {
        jlinkArgs.add(argument);
    }

    private void addArgument(String argument, Path path) {
        addArgument(argument, path.normalize().toString());
    }

    private void addArgument(String argument, String value) {
        jlinkArgs.add(argument);
        jlinkArgs.add(value);
    }
}

