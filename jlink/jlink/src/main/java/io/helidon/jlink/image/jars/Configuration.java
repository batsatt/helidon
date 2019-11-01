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

import io.helidon.jlink.common.util.FileUtils;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;
import static io.helidon.jlink.common.util.FileUtils.assertFile;

/**
 * Linker configuration.
 */
public class Configuration {
    private JavaHome javaHome;
    private Path applicationJar;
    private Path imageDirectory;
    private boolean verbose;
    private boolean stripDebug;

    public static Builder builder() {
        return new Builder();
    }

    private Configuration(Builder builder) {
        this.javaHome = builder.javaHome;
        this.applicationJar = builder.applicationJar;
        this.imageDirectory = builder.imageDirectory;
        this.verbose = builder.verbose;
        this.stripDebug = builder.stripDebug;
    }

    public JavaHome javaHome() {
        return javaHome;
    }

    public Path applicationJar() {
        return applicationJar;
    }

    public Path imageDirectory() {
        return imageDirectory;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isStripDebug() {
        return stripDebug;
    }

    public static class Builder {
        private Path javaHomeDir;
        private Path applicationJar;
        private Path imageDirectory;
        private boolean verbose;
        private boolean stripDebug;
        private JavaHome javaHome;

        private Builder() {
            javaHomeDir = CURRENT_JAVA_HOME_DIR;
        }

        public Builder commandLine(String... args) {
            for (int i = 0; i < args.length; i++) {
                final String arg = args[i];
                if (arg.startsWith("--")) {
                    if (arg.equalsIgnoreCase("--javaHome")) {
                        javaHome(Paths.get(argAt(++i, args)));
                    } else if (arg.equalsIgnoreCase("--imageDir")) {
                        imageDirectory(Paths.get(argAt(++i, args)));
                    } else if (arg.equalsIgnoreCase("--strip-debug")) {
                        stripDebug(true);
                    } else if (arg.equalsIgnoreCase("--verbose")) {
                        verbose(true);
                    } else {
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                } else if (applicationJar == null) {
                    applicationJar(FileUtils.assertExists(Paths.get(arg)));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return this;
        }

        public Builder javaHome(Path javaHomeDir) {
            this.javaHomeDir = javaHomeDir;
            return this;
        }

        public Builder applicationJar(Path applicationJar) {
            this.applicationJar = assertFile(applicationJar);
            return this;
        }

        public Builder imageDirectory(Path imageDirectory) {
            this.imageDirectory = imageDirectory;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder stripDebug(boolean stripDebug) {
            this.stripDebug = stripDebug;
            return this;
        }

        public Configuration build() throws Exception {
            if (applicationJar == null) {
                throw new IllegalArgumentException("applicationJar required");
            }
            javaHome = new JavaHome(javaHomeDir).assertHasJmodFiles();
            imageDirectory = FileUtils.prepareImageDir(imageDirectory, applicationJar);
            return new Configuration(this);
        }

        private String argAt(int index, String[] args) {
            if (index < args.length) {
                return args[index];
            } else {
                throw new IllegalArgumentException("missing argument"); // TODO usage()
            }
        }
    }
}
