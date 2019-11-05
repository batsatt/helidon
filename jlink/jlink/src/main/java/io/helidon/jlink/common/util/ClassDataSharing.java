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

package io.helidon.jlink.common.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.jlink.common.logging.Log;

import static io.helidon.jlink.common.util.FileUtils.assertDir;
import static io.helidon.jlink.common.util.FileUtils.assertFile;
import static java.util.Objects.requireNonNull;

/**
 * A builder for a CDS archive.
 */
public class ClassDataSharing {
    private final Path applicationJar;
    private final Path javaHome;
    private final Path classListFile;
    private final Path archiveFile;
    private final List<String> classList;

    public static Builder builder() {
        return new Builder();
    }

    public static ClassDataSharing create(Path javaHome, Path applicationJar) throws Exception {
        return builder().jre(javaHome)
                        .applicationJar(applicationJar)
                        .build();
    }

    private ClassDataSharing(Builder builder) {
        this.applicationJar = builder.applicationJar;
        this.javaHome = builder.javaHome;
        this.classListFile = builder.classListFile;
        this.archiveFile = builder.archiveFile;
        this.classList = builder.classList;
    }

    public Path applicationJar() {
        return applicationJar;
    }

    public Path javaHome() {
        return javaHome;
    }

    public Path classListFile() {
        return classListFile;
    }

    public Path archiveFile() {
        return archiveFile;
    }

    public List<String> classList() {
        return classList;
    }

    public static class Builder {
        private static final Log LOG = Log.getLog("class-data-sharing");
        private static final String FILE_PREFIX = "server";
        private static final String CLASS_LIST_FILE_SUFFIX = ".classlist";
        private static final String JAR_SUFFIX = ".jar";
        private static final String FILE_SEP = File.separator;
        private static final String JAVA_CMD_PATH = "bin" + FILE_SEP + "java";
        private static final String XSHARE_OFF = "-Xshare:off";
        private static final String XSHARE_DUMP = "-Xshare:dump";
        private static final String XX_DUMP_LOADED_CLASS_LIST = "-XX:DumpLoadedClassList=";
        private static final String XX_SHARED_ARCHIVE_FILE = "-XX:SharedArchiveFile=";
        private static final String XX_SHARED_CLASS_LIST_FILE = "-XX:SharedClassListFile=";
        private static final String EXIT_ON_STARTED = "-Dexit.on.started";
        private static final String LIB_DIR_NAME = "lib";
        private static final String ARCHIVE_NAME = FILE_PREFIX + ".jsa";
        private static final String CLASS_SUFFIX = ".class";
        private static final String MODULE_INFO_NAME = "module-info";
        private static final String BEAN_ARCHIVE_SCANNER = "org/jboss/weld/environment/deployment/discovery/BeanArchiveScanner";
        private Path javaHome;
        private String archiveDir;
        private String moduleName;
        private Path applicationJar;
        private Path classListFile;
        private Path archiveFile;
        private List<String> classList;
        private boolean createArchive;
        private Path weldJrtJar;
        private String target;
        private String targetOption;
        private String targetDescription;
        private Log outputLog;


        private Builder() {
            this.createArchive = true;
            this.archiveDir = LIB_DIR_NAME;
        }

        public Builder jre(Path javaHome) {
            this.javaHome = javaHome;
            javaPath(); // Validate
            return this;
        }

        public Builder applicationJar(Path applicationJar) {
            if (requireNonNull(applicationJar).isAbsolute()) {
                this.applicationJar = assertJar(applicationJar);
            } else {

                this.applicationJar = assertJar(javaHome.resolve(applicationJar));
            }
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = requireNonNull(moduleName);
            return this;
        }

        public Builder archiveFile(Path archiveFile) {
            this.archiveFile = requireNonNull(archiveFile);
            return this;
        }

        public Builder weldJrtJar(Path weldJrtJar) {
            this.weldJrtJar = weldJrtJar == null ? null : assertJar(weldJrtJar);
            return this;
        }

        public Builder showOutput(boolean showOutput) {
            this.outputLog = showOutput ? LOG : null;
            return this;
        }

        public Builder classListFile(Path classListFile) {
            this.classListFile = assertFile(classListFile);
            return this;
        }

        public Builder createArchive(boolean createArchive) {
            this.createArchive = createArchive;
            return this;
        }

        public ClassDataSharing build() throws Exception {
            requireNonNull(javaHome, "java home required");
            if (applicationJar == null && moduleName == null) {
                throw new IllegalStateException("Either application jar or module name required");
            } else if (applicationJar != null && moduleName != null) {
                throw new IllegalStateException("Cannot specify both application jar and module name");
            } else if (applicationJar != null) {
                this.targetOption = "-jar";
                this.target = applicationJar.toString();
                this.targetDescription = target;
            } else {
                this.targetOption = "-m";
                this.target = moduleName;
                this.targetDescription = "module " + target + " in " + javaHome;
            }

            if (classListFile == null) {
                this.classListFile = tempFile(CLASS_LIST_FILE_SUFFIX);
                this.classList = buildClassList();
            } else {
                this.classList = loadClassList();
            }

            updateClassList();

            if (createArchive) {
                if (archiveFile == null) {
                    archiveFile = assertDir(javaHome.resolve(archiveDir)).resolve(ARCHIVE_NAME);
                }
                buildCdsArchive();
            }

            return new ClassDataSharing(this);
        }

        private void updateClassList() throws IOException {
            if (weldJrtJar != null) {
                final int beanArchiveScannerIndex = classList.indexOf(BEAN_ARCHIVE_SCANNER);
                if (beanArchiveScannerIndex > 0) {
                    try (final JarFile jar = new JarFile(weldJrtJar.toFile())) {
                        final List<String> classes = new ArrayList<>();
                        final Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            final JarEntry entry = entries.nextElement();
                            final String name = entry.getName();
                            if (name.endsWith(CLASS_SUFFIX) && !name.startsWith(MODULE_INFO_NAME)) {
                                classes.add(name.substring(0, name.length() - CLASS_SUFFIX.length()));
                            }
                        }
                        classList.addAll(beanArchiveScannerIndex, classes);
                    }
                } else {
                    LOG.warn("weldJrtJar provided but %s not found", BEAN_ARCHIVE_SCANNER);
                }
            }
        }

        private List<String> buildClassList() throws Exception {
            execute("Building startup class list for " + targetDescription,
                    XSHARE_OFF, XX_DUMP_LOADED_CLASS_LIST + classListFile);
            return loadClassList();
        }

        private void buildCdsArchive() throws Exception {
            execute("Building CDS archive for " + targetDescription,
                    XSHARE_DUMP, XX_SHARED_ARCHIVE_FILE + archiveFile, XX_SHARED_CLASS_LIST_FILE + classListFile);
        }

        private List<String> loadClassList() throws IOException {
            return Files.readAllLines(classListFile);
        }

        private void execute(String action, String... jvmArgs) throws Exception {
            final ProcessBuilder builder = new ProcessBuilder();
            final List<String> command = new ArrayList<>();

            command.add(javaPath().toString());
            command.add(EXIT_ON_STARTED);
            command.addAll(Arrays.asList(jvmArgs));
            command.add(targetOption);
            command.add(target);
            builder.command(command);

            builder.directory(javaHome.toFile());

            ProcessMonitor.newMonitor(builder, action, outputLog).run();
        }

        private Path javaPath() {
            return JavaRuntime.javaCommand(javaHome);
        }

        private static Path tempFile(String suffix) throws IOException {
            final File file = File.createTempFile(FILE_PREFIX, suffix);
            file.deleteOnExit();
            return file.toPath();
        }

        private static Path assertJar(Path path) {
            final String fileName = assertFile(path).getFileName().toString();
            if (!fileName.endsWith(JAR_SUFFIX)) {
                throw new IllegalArgumentException(path + " is not a jar");
            }
            return path;
        }
    }
}
