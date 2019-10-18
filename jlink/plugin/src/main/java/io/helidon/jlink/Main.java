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

package io.helidon.jlink;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;

import io.helidon.jlink.logging.Log;
import io.helidon.jlink.plugins.ApplicationContext;
import io.helidon.jlink.plugins.BootOrderPlugin;
import io.helidon.jlink.plugins.ClassDataSharing;
import io.helidon.jlink.plugins.HelidonPlugin;

import jdk.tools.jlink.plugin.PluginException;

/**
 * Wrapper for jlink to handle custom options.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        new Linker().parse(args)
                    .buildHelidonPluginArguments()
                    .buildJlinkArguments()
                    .buildImage()
                    .addCdsArchive()
                    .complete();
    }

    private static class Linker {
        private static final Log LOG = Log.getLog("launcher");
        private static final Path JAVA_HOME_DIR = Paths.get(System.getProperty("java.home"));
        private static final Path CURRENT_DIR = Paths.get(".");
        private static final String IMAGE_SUFFIX = "-image";
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
            this.javaHome = JAVA_HOME_DIR;
            this.helidonPluginArgs = new StringBuilder();
            this.jlink = ToolProvider.findFirst("jlink").orElseThrow();
        }

        private Linker buildImage() {
            final int result = jlink.run(System.out, System.err, jlinkArgs.toArray(new String[0]));
            if (result != 0) {
                throw new Error("Image creation failed.");
            }
            return this;
        }

        private Linker addCdsArchive() {
            try {
                final ApplicationContext context = ApplicationContext.get();
                final ClassDataSharing cds = ClassDataSharing.builder()
                                                             .javaHome(imageDir)
                                                             .moduleName(context.applicationModuleName())
                                                             .classListFile(context.classListFile())
                                                             .showOutput(true)
                                                             .build();
                LOG.info("Added %s", cds.archiveFile());
            } catch (Exception e) {
                throw new PluginException(e);
            }
            return this;
        }

        private Linker complete() {
            LOG.info("Created " + imageDir);
            return this;
        }

        private Linker parse(String... args) throws Exception {
            this.cmdLineArgs = args;
            for (int i = 0; i < args.length; i++) {
                final String arg = args[i];
                if (arg.startsWith("--")) {
                    if (arg.equalsIgnoreCase("--patchesDir")) {
                        patchesDir = assertDir(Paths.get(argAt(++i)));
                    } else if (arg.equalsIgnoreCase("--javaHome")) {
                        javaHome = assertDir(Paths.get(argAt(++i)));
                        assertDir(javaHome.resolve("jmods"));
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
                    appModulePath = assertExists(Paths.get(arg));
                }
            }

            if (appModulePath == null) {
                throw new IllegalArgumentException("applicationModulePath required");
            } else {
                assertDir(appModulePath.getParent().resolve("libs"));
            }

            imageDir = prepareImageDir();

            return this;
        }

        Linker buildHelidonPluginArguments() {

            // Tell our plugin where the main application module is.
            // NOTE: jlink quirk here, where the first argument cannot be named.

            appendHelidonPluginArg(null, appModulePath);

            // Tell our plugin where the weld-jrt.jar is

            final Path ourModule = getModulePath(getClass());
            final Path weldJrtModule = assertExists(ourModule.getParent().resolve(WELD_JRT_JAR_PATH));
            appendHelidonPluginArg(HelidonPlugin.WELD_JRT_MODULE_KEY, weldJrtModule);

            // Tell our plugin what JDK to use

            appendHelidonPluginArg(HelidonPlugin.JAVA_HOME_KEY, javaHome);

            // Tell our plugin where the patches live, if provided

            if (patchesDir != null) {
                appendHelidonPluginArg(HelidonPlugin.PATCHES_DIR_KEY, patchesDir);
            }

            return this;
        }

        Linker buildJlinkArguments() {

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

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private Path prepareImageDir() throws IOException {
            if (imageDir == null) {
                final String jarName = appModulePath.getFileName().toString();
                final String dirName = jarName.substring(0, jarName.lastIndexOf('.')) + IMAGE_SUFFIX;
                imageDir = CURRENT_DIR.resolve(dirName);
            }
            if (Files.exists(imageDir)) {
                if (Files.isDirectory(imageDir)) {
                    Files.walk(imageDir)
                         .sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                } else {
                    throw new IllegalArgumentException(imageDir + " is not a directory");
                }
            }
            return imageDir;
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

        private static Path assertDir(Path dir) {
            if (Files.isDirectory(dir)) {
                return dir.toAbsolutePath().normalize();
            } else {
                throw new IllegalArgumentException(dir + " is not a directory");
            }
        }

        private static Path assertExists(Path path) {
            if (Files.exists(path)) {
                return path.toAbsolutePath().normalize();
            } else {
                throw new IllegalArgumentException(path + " does not exist");
            }
        }
    }
}
