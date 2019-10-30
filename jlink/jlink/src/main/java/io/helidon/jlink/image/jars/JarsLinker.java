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

package io.helidon.jlink.image.jars;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.ClassDataSharing;
import io.helidon.jlink.common.util.FileUtils;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;

/**
 * Create a JDK image by finding the JDK modules required of a Helidon application and linking
 * them via jlink, then adding the jars and a CDS archive.
 */
public class JarsLinker {
    private static final Log LOG = Log.getLog("jars-linker");
    private static final String JLINK_TOOL_NAME = "jlink";
    private final ToolProvider jlink;
    private final List<String> jlinkArgs;
    private String[] cmdLineArgs;
    private boolean stripDebug;
    private boolean verbose;
    private JavaHome javaHome;
    private Path appJar;
    private Path imageDir;
    private Application application;
    private Set<String> jdkDependencies;
    private JavaHome imageHome;
    private Path imageApplicationJar;

    public static void main(String... args) throws Exception {
        link(args);
    }

    static Path link(String... args) throws Exception {
        final JarsLinker linker = new JarsLinker().configure(args)
                                                  .buildApplication()
                                                  .collectJdkDependencies()
                                                  .buildJlinkArguments()
                                                  .buildImage()
                                                  .copyJars()
                                                  .addCdsArchive()
                                                  .complete();
        return linker.imageDir;
    }

    private JarsLinker() {
        this.jlink = ToolProvider.findFirst(JLINK_TOOL_NAME).orElseThrow();
        this.jlinkArgs = new ArrayList<>();
        System.setProperty("jlink.debug", "true"); // TODO
    }

    private JarsLinker complete() {
        LOG.info("Image completed: %s", imageDir);
        return this;
    }

    private JarsLinker addCdsArchive() {
        try {
            ClassDataSharing.builder()
                            .javaHome(imageHome.path())
                            .applicationJar(imageApplicationJar)
                            .showOutput(verbose)
                            .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private JarsLinker copyJars() {
        LOG.info("Copying %d application jars to %s", application.applicationLibJars().size() + 1, imageDir);
        final Path appDir = imageHome.applicationDir();
        final Path appLibsDir = imageHome.applicationLibsDir();
        this.imageApplicationJar = application.applicationJar().copyToDirectory(appDir);
        application.applicationLibJars().forEach(jar -> jar.copyToDirectory(appLibsDir));
        return this;
    }

    private JarsLinker buildImage() {
        LOG.info("Building image: %s", imageDir);
        final int result = jlink.run(System.out, System.err, jlinkArgs.toArray(new String[0]));
        if (result != 0) {
            throw new Error("Image creation failed.");
        }
        imageHome = new JavaHome(imageDir, javaHome.version());
        return this;
    }

    private JarsLinker buildJlinkArguments() {

        // Tell jlink which jdk modules to include

        addArgument("--add-modules", String.join(",", jdkDependencies));

        // Tell jlink the directory in which to create and write the image

        addArgument("--output", imageDir);

        // Tell jlink to strip out unnecessary stuff

        if (stripDebug) {
            addArgument("--strip-debug");
        }
        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        addArgument("--compress", "2");

        return this;
    }

    private JarsLinker collectJdkDependencies() {
        LOG.info("Collecting JDK module dependencies");
        final JdkDependencies dependencies = new JdkDependencies(javaHome);
        this.jdkDependencies = dependencies.collect(application.jars());
        LOG.info("JDK module dependencies: %s", String.join(", ", jdkDependencies));
        return this;
    }

    private JarsLinker buildApplication() {
        LOG.info("Loading application jars");
        this.application = new Application(javaHome, appJar);
        return this;
    }

    private JarsLinker configure(String... args) throws Exception {
        Path javaHomeDir = CURRENT_JAVA_HOME_DIR;
        this.cmdLineArgs = args;
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (arg.startsWith("--")) {
                if (arg.equalsIgnoreCase("--javaHome")) {
                    javaHomeDir = Paths.get(argAt(++i));
                } else if (arg.equalsIgnoreCase("--imageDir")) {
                    imageDir = Paths.get(argAt(++i));
                } else if (arg.equalsIgnoreCase("--strip-debug")) {
                    stripDebug = true;
                } else if (arg.equalsIgnoreCase("--verbose")) {
                    verbose = true;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            } else if (appJar == null) {
                appJar = FileUtils.assertExists(Paths.get(arg));
            }
        }

        if (appJar == null) {
            throw new IllegalArgumentException("applicationPath required");
        } else {
            FileUtils.assertDir(appJar.getParent().resolve("libs"));
        }

        javaHome = new JavaHome(javaHomeDir).assertHasJmodFiles();

        imageDir = FileUtils.prepareImageDir(imageDir, appJar);

        return this;
    }

    private String argAt(int index) {
        if (index < cmdLineArgs.length) {
            return cmdLineArgs[index];
        } else {
            throw new IllegalArgumentException("missing argument"); // TODO usage()
        }
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
