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
import java.util.spi.ToolProvider;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.FileUtils;
import io.helidon.jlink.image.modules.plugins.ApplicationContext;
import io.helidon.jlink.image.modules.plugins.BootOrderPlugin;
import io.helidon.jlink.common.util.ClassDataSharing;
import io.helidon.jlink.image.modules.plugins.HelidonPlugin;

import jdk.tools.jlink.plugin.PluginException;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;

/**
 * Create a JDK image by mapping all jars of a Helidon application into modules and linking
 * them via jlink, then adding a CDS archive.
 */
public class Linker {

    public static void main(String[] args) throws Exception {
        new Linker().parse(args)
                    .buildHelidonPluginArguments()
                    .buildJlinkArguments()
                    .buildImage()
                    .addCdsArchive()
                    .complete();
    }

    private static final Log LOG = Log.getLog("linker");
    private static final String WELD_JRT_JAR_PATH = "libs/helidon-weld-jrt.jar";
    private final List<String> jlinkArgs;
    private String[] cmdLineArgs;
    private boolean stripDebug;
    private Path javaHome;
    private Path patchesDir;
    private Path appModulePath;
    private Path imageDir;
    private StringBuilder helidonPluginArgs;
    private final ToolProvider jlink;

    private Linker() {
        this.jlinkArgs = new ArrayList<>();
        this.javaHome = CURRENT_JAVA_HOME_DIR;
        this.helidonPluginArgs = new StringBuilder();
        this.jlink = ToolProvider.findFirst("jlink").orElseThrow();
        System.setProperty("jlink.debug", "true"); // TODO
    }

    private Linker buildImage() {
        final int result = jlink.run(System.out, System.err, jlinkArgs.toArray(new String[0]));
        if (result != 0) {
            throw new Error("Image creation failed.");
        }
        LOG.info("Image created: %s", imageDir);
        return this;
    }

    private Linker addCdsArchive() {
        try {
            final ApplicationContext context = ApplicationContext.get();
            final ClassDataSharing cds = ClassDataSharing.builder()
                                                         .javaHome(imageDir)
                                                         .moduleName(context.applicationModuleName())
                                                         .showOutput(true)
                                                         .build();
            LOG.info("Added CDS archive %s", cds.archiveFile());
        } catch (Exception e) {
            throw new PluginException(e);
        }
        return this;
    }

    private void complete() {
        LOG.info("Image completed: %s", imageDir);
    }

    private Linker parse(String... args) throws Exception {
        this.cmdLineArgs = args;
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (arg.startsWith("--")) {
                if (arg.equalsIgnoreCase("--patchesDir")) {
                    patchesDir = FileUtils.assertNonEmptyDir(Paths.get(argAt(++i)));
                } else if (arg.equalsIgnoreCase("--javaHome")) {
                    javaHome = FileUtils.assertDir(Paths.get(argAt(++i)));
                    FileUtils.assertDir(javaHome.resolve("jmods"));
                } else if (arg.equalsIgnoreCase("--imageDir")) {
                    imageDir = Paths.get(argAt(++i));
                } else if (arg.equalsIgnoreCase("--verbose")) {
                    addArgument("--verbose");
                } else if (arg.equalsIgnoreCase("--strip-debug")) {
                    stripDebug = true;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            } else if (appModulePath == null) {
                appModulePath = FileUtils.assertExists(Paths.get(arg));
            }
        }

        if (patchesDir == null) {
            throw new IllegalArgumentException("patchesDir required");
        }

        if (appModulePath == null) {
            throw new IllegalArgumentException("applicationModulePath required");
        } else {
            FileUtils.assertDir(appModulePath.getParent().resolve("libs"));
        }

        imageDir = FileUtils.prepareImageDir(imageDir, appModulePath);

        return this;
    }

    private Linker buildHelidonPluginArguments() {

        // Tell our plugin where the main application module is.
        // NOTE: jlink quirk here, where the first argument cannot be named.

        appendHelidonPluginArg(null, appModulePath);

        // Tell our plugin where the weld-jrt.jar is

        final Path ourModule = getModulePath(getClass());
        final Path weldJrtModule = FileUtils.assertExists(ourModule.getParent().resolve(WELD_JRT_JAR_PATH));
        appendHelidonPluginArg(HelidonPlugin.WELD_JRT_MODULE_KEY, weldJrtModule);

        // Tell our plugin what JDK to use

        appendHelidonPluginArg(HelidonPlugin.JAVA_HOME_KEY, javaHome);

        // Tell our plugin where the patches live, if provided

        if (patchesDir != null) {
            appendHelidonPluginArg(HelidonPlugin.PATCHES_DIR_KEY, patchesDir);
        }

        return this;
    }

    private Linker buildJlinkArguments() {

        // Tell jlink to use our plugins

        addArgument("--" + HelidonPlugin.NAME + "=" + helidonPluginArgs.toString());
        addArgument("--" + BootOrderPlugin.NAME);

        // Tell jlink to use our BootModulesPlugin instead of the SystemModulesPlugin

        addArgument("--disable-plugin", "system-modules");
        addArgument("--boot-modules");

        // Tell jlink the directory to create and write the image

        addArgument("--output", imageDir);

        // Since we expect Helidon and dependencies to leverage automatic modules, we
        // can't configure jlink with the app and libs directly. It requires some module,
        // however, so just add a place holder; we won't add it to the image.

        addArgument("--add-modules", "java.logging");

        // Tell jlink to resolve and include the service providers

        addArgument("--bind-services");

        // Tell jlink to strip out unnecessary stuff

        if (stripDebug) {
            addArgument("--strip-debug");
        }
        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        addArgument("--compress", "2");

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

    private String argAt(int index) {
        if (index < cmdLineArgs.length) {
            return cmdLineArgs[index];
        } else {
            throw new IllegalArgumentException("missing argument"); // TODO usage()
        }
    }
}

