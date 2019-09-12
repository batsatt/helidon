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

/**
 * TODO: Describe
 */
public class Main {

    // Ref: https://in.relation.to/2017/12/12/exploring-jlink-plugin-api-in-java-9/

    /*

java ${JAVA_DEBUG} -javaagent:./agent/target/helidon-jlink-agent.jar --module-path ./plugin/target/helidon-jlink.jar --module helidon.jlink --appModulePath /Users/batsatt/dev/load-testing/helidon-mp/target/helidon-mp.jar


        Invoking this tool the jlink wrapper (cleaner than using -J-*) and agent jar:

        java -javaagent:./agent/target/helidon-jlink-agent.jar --module-path ./plugin/target/helidon-jlink.jar --module helidon.jlink

        java -javaagent:path/to/helidon-jlink-agent.jar \
        --module-path path/to/helidon-jlink.jar \  // OR directory containing jar
        --module helidon.jlink \
        --module-path $JAVA_HOME/jmods/ \
        --module-path path/to/application-jar-or-dir \
        --module-path path/to/application-lib-dir \
        --add-modules helidon \   ??
        --add-index=com.example.b:for-modules=com.example.a
        --output path/to/jlink-image \

     */

    // TODO: create jandex index if needed

    private static final Path JAVA_HOME_DIR = Paths.get(System.getProperty("java.home"));
    private static final Path CURRENT_DIR = Paths.get(".");

    private final String[] args;
    private final List<String> jlinkArgs;
    private Path jmodsDir;
    private Path jmodsOverridesDir;
    private Path appModulePath;
    private Path imageDir;
    private final ToolProvider jlink;

    public static void main(String[] args) {
        new Main(args).run();
    }

    private Main(String... args) {
        this.args = args;
        this.jlinkArgs = new ArrayList<>();
        this.jmodsDir = JAVA_HOME_DIR.resolve("jmods");
        this.imageDir = CURRENT_DIR.resolve("hlink-image").toAbsolutePath();
        this.jlink = ToolProvider.findFirst("jlink").orElseThrow();
        parse();
    }

    private void run() {
        jlink.run(
            System.out,
            System.err,
            jlinkArgs.toArray(new String[0])
        );
    }

    private void parse() {
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (arg.equalsIgnoreCase("--jmodOverridesDir")) {
                jmodsOverridesDir = assertDir(Paths.get(argAt(++i)));
            } else if (arg.equalsIgnoreCase("--jmodsDir")) {
                jmodsDir = assertDir(Paths.get(argAt(++i)));
            } else if (arg.equalsIgnoreCase("--imageDir")) {
                imageDir = assertDir(Paths.get(argAt(++i)));
                if (Files.exists(imageDir)) {
                    throw new IllegalArgumentException("Output dir " + imageDir + " already exists.");
                }
            } else if (appModulePath == null) {
                appModulePath = assertExists(Paths.get(arg));
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (jmodsOverridesDir != null) {
            addModulePath(jmodsOverridesDir);
        }
        addModulePath(jmodsDir);

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

        addArgument("--output", imageDir);

        addArgument("--helidon", appModulePath);

        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        addArgument("--strip-debug"); // TODO: option?
        addArgument("--compress", "2");
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
            return dir;
        } else {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
    }

    private static Path assertExists(Path path) {
        if (Files.exists(path)) {
            return path;
        } else {
            throw new IllegalArgumentException(path + " does not exist");
        }
    }
}
