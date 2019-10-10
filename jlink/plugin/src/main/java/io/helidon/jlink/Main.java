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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;

import io.helidon.jlink.plugins.HelidonPlugin;

/**
 * Wrapper for jlink to handle custom options.
 */
public class Main {

    // TODO: create jandex index if needed

    // TODO: AppCDS ! See https://jdk.java.net/13/release-notes (search for "AppCDS") for
    //              new -XX:ArchiveClassesAtExit=hello.jsa option; consider modifying server
    //              so we can start app with this option to record archive.

    private static final Path JAVA_HOME_DIR = Paths.get(System.getProperty("java.home"));
    private static final Path CURRENT_DIR = Paths.get(".");

    private final String[] args;
    private final List<String> jlinkArgs;
    private Path javaHome;
    private Path patchesDir;
    private Path appModulePath;
    private Path imageDir;
    private StringBuilder pluginArgs;
    private final ToolProvider jlink;

    public static void main(String[] args) {
        new Main(args).run();
    }

    private Main(String... args) {
        this.args = args;
        this.jlinkArgs = new ArrayList<>();
        this.javaHome = JAVA_HOME_DIR;
        this.imageDir = CURRENT_DIR.resolve("hlink-image").toAbsolutePath();
        this.pluginArgs = new StringBuilder();
        this.jlink = ToolProvider.findFirst("jlink").orElseThrow();
        parse();
    }

    private void run() {
        jlink.run(
            System.out,
            System.err,
            jlinkArgs.toArray(new String[0])
        );
        System.out.println("Done.");
    }

    private void parse() {
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (arg.startsWith("--")) {
                if (arg.equalsIgnoreCase("--patchesDir")) {
                    patchesDir = assertDir(Paths.get(argAt(++i)));
                } else if (arg.equalsIgnoreCase("--javaHome")) {
                    javaHome = assertDir(Paths.get(argAt(++i)));
                    assertDir(javaHome.resolve("jmods"));
                } else if (arg.equalsIgnoreCase("--imageDir")) {
                    imageDir = assertDir(Paths.get(argAt(++i)));
                    if (Files.exists(imageDir)) {
                        throw new IllegalArgumentException("Output dir " + imageDir + " already exists.");
                    }
                } else if (arg.equalsIgnoreCase("--verbose")) {
                    addArgument("--verbose");
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

            // Since we expect Helidon and dependencies to leverage automatic modules, we
            // can't configure jlink with the app and libs directly. It requires some module,
            // however, so just add this one. We won't include it in the output.

            Class<?> thisClass = getClass();
            String thisModuleName = thisClass.getModule().getName();
            Path thisModulePath = getModulePath(thisClass);
            addModulePath(thisModulePath);
            addArgument("--add-modules", thisModuleName);
        }

        appendPluginArg(null, appModulePath);
        appendPluginArg(HelidonPlugin.JAVA_HOME_KEY, javaHome);
        if (patchesDir != null) {
            appendPluginArg(HelidonPlugin.PATCHES_DIR_KEY, patchesDir);
        }
        addArgument("--" + HelidonPlugin.NAME + "=" + pluginArgs.toString());

        // Swap our variant of SystemModulesPlugin for the default one

        addArgument("--disable-plugin","system-modules");
        addArgument("--boot-modules");

        addArgument("--output", imageDir);
        addArgument("--bind-services");
        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        // addArgument("--strip-debug"); // TODO: option
        addArgument("--compress", "2");
    }

    private void appendPluginArg(String key, Path value) {
        if (pluginArgs.length() > 0) {
            pluginArgs.append(':');
        }
        if (key != null) {
            pluginArgs.append(key).append('=');
        }
        pluginArgs.append(value);
    }

    private static Path getModulePath(Class<?> moduleClass) {
        return Paths.get(moduleClass.getProtectionDomain().getCodeSource().getLocation().getPath());
    }

    private void addModulePath(Path path) {
        addArgument("--module-path", path);
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
        if (index < args.length) {
            return args[index];
        } else {
            throw new IllegalArgumentException("missing argument"); // TODO usage()
        }
    }

    private static Path assertDir(Path dir) {
        if (Files.isDirectory(dir)) {
            return dir.toAbsolutePath();
        } else {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
    }

    private static Path assertExists(Path path) {
        if (Files.exists(path)) {
            return path.toAbsolutePath();
        } else {
            throw new IllegalArgumentException(path + " does not exist");
        }
    }
}
