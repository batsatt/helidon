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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.ClassDataSharing;

/**
 * Create a JDK image by finding the Java modules required of a Helidon application and linking
 * them via jlink, then adding the jars and a CDS archive. Adds Jandex indices as needed.
 */
public class JarsLinker {
    private static final Log LOG = Log.getLog("jars-linker");
    private static final String JLINK_TOOL_NAME = "jlink";
    private final ToolProvider jlink;
    private final List<String> jlinkArgs;
    private Configuration config;
    private Application application;
    private Set<String> javaDependencies;
    private JavaHome imageHome;
    private Path imageApplicationJar;

    /**
     * Main entry point.
     *
     * @param args Command line arguments.
     * @throws Exception If an error occurs.
     */
    public static void main(String... args) throws Exception {
        final Configuration config = Configuration.builder().commandLine(args).build();
        linker(config).link();
    }

    /**
     * Returns a new linker with the given configuration.
     *
     * @param config The configuration.
     * @return The linker.
     */
    public static JarsLinker linker(Configuration config) {
        return new JarsLinker(config);
    }

    private JarsLinker(Configuration config) {
        this.jlink = ToolProvider.findFirst(JLINK_TOOL_NAME).orElseThrow();
        this.jlinkArgs = new ArrayList<>();
        this.config = config;
        if (config.isVerbose()) {
            System.setProperty("jlink.debug", "true");
        }
    }

    /**
     * Create the image.
     *
     * @return The image directory.
     */
    public Path link() {
        buildApplication();
        collectJavaDependencies();
        buildJlinkArguments();
        buildImage();
        copyJars();
        addCdsArchive();
        complete();
        return config.imageDirectory();
    }

    /**
     * Returns the configuration.
     *
     * @return The configuration.
     */
    public Configuration config() {
        return config;
    }

    private void buildApplication() {
        LOG.info("Loading application jars");
        this.application = new Application(config.applicationJar());
    }

    private void collectJavaDependencies() {
        LOG.info("Collecting Java module dependencies");
        this.javaDependencies = application.javaDependencies(config.javaHome());
        LOG.info("Found %d Java module dependencies: %s", javaDependencies.size(), String.join(", ", javaDependencies));
    }

    private void buildJlinkArguments() {

        // Tell jlink which jdk modules to include

        addArgument("--add-modules", String.join(",", javaDependencies));

        // Tell jlink the directory in which to create and write the image

        addArgument("--output", config.imageDirectory());

        // Tell jlink to strip out unnecessary stuff

        if (config.isStripDebug()) {
            addArgument("--strip-debug");
        }
        addArgument("--no-header-files");
        addArgument("--no-man-pages");
        addArgument("--compress", "2");
    }

    private void buildImage() {
        LOG.info("Building image: %s", config.imageDirectory());
        final int result = jlink.run(System.out, System.err, jlinkArgs.toArray(new String[0]));
        if (result != 0) {
            throw new Error("Image creation failed.");
        }
        imageHome = new JavaHome(config.imageDirectory(), config.javaHome().version());
    }

    private void copyJars() {
        LOG.info("Copying %d application jars to %s", application.size(), config.imageDirectory());
        this.imageApplicationJar = application.install(imageHome);
    }

    private void addCdsArchive() {
        try {
            ClassDataSharing.builder()
                            .javaHome(imageHome.path())
                            .applicationJar(imageApplicationJar)
                            .showOutput(config.isVerbose())
                            .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void complete() {
        LOG.info("Image completed: %s", config.imageDirectory());
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
