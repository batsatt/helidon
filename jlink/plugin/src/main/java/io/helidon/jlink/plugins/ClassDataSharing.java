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

package io.helidon.jlink.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.jlink.logging.Log;

import static java.util.Objects.requireNonNull;

/**
 * TODO: Describe
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
        return builder().javaHome(javaHome)
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
        private static final String FILE_PREFIX = "helidon";
        private static final String CLASS_LIST_FILE_SUFFIX = ".classlist";
        private static final String JAR_SUFFIX = ".jar";
        private static final String FILE_SEP = File.separator;
        private static final String JAVA_CMD_PATH = "bin" + FILE_SEP + "java";
        private static final ExecutorService EXECUTOR = ForkJoinPool.commonPool();
        private static final String XSHARE_OFF = "-Xshare:off";
        private static final String XSHARE_DUMP = "-Xshare:dump";
        private static final String XX_DUMP_LOADED_CLASS_LIST = "-XX:DumpLoadedClassList=";
        private static final String XX_SHARED_ARCHIVE_FILE = "-XX:SharedArchiveFile=";
        private static final String XX_SHARED_CLASS_LIST_FILE = "-XX:SharedClassListFile=";
        private static final String EXIT_ON_STARTED = "-Dexit.on.started";
        private static final String LIB_DIR_NAME = "lib";
        private static final String ARCHIVE_NAME = "helidon.jsa";
        private static final String CLASS_SUFFIX = ".class";
        private static final String MODULE_INFO_NAME = "module-info";
        private static final String BEAN_ARCHIVE_SCANNER = "org/jboss/weld/environment/deployment/discovery/BeanArchiveScanner";
        private static final String EOL = System.getProperty("line.separator");
        private String moduleName;
        private Path applicationJar;
        private Path javaHome;
        private Path classListFile;
        private Path archiveFile;
        private List<String> classList;
        private boolean createArchive;
        private boolean showOutput;
        private Path weldJrtJar;
        private String target;
        private String targetOption;
        private String targetDescription;

        private Builder() {
            this.createArchive = true;
        }

        public Builder javaHome(Path javaHome) {
            this.javaHome = assertDir(requireNonNull(javaHome));
            javaPath(); // Validate
            return this;
        }

        public Builder applicationJar(Path applicationJar) {
            this.applicationJar = assertJar(requireNonNull(applicationJar));
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = requireNonNull(moduleName);
            return this;
        }

        public Builder weldJrtJar(Path weldJrtJar) {
            this.weldJrtJar = assertJar(weldJrtJar);
            return this;
        }

        public Builder showOutput(boolean showOutput) {
            this.showOutput = showOutput;
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
                this.targetDescription = "module " + target;
            }

            if (classListFile == null) {
                this.classListFile = tempFile(CLASS_LIST_FILE_SUFFIX);
                this.classList = buildClassList();
            } else {
                this.classList = loadClassList();
            }

            updateClassList();

            if (createArchive) {
                final Path libsDir = Builder.assertDir(requireNonNull(javaHome).resolve(LIB_DIR_NAME));
                this.archiveFile = libsDir.resolve(ARCHIVE_NAME);
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
            LOG.info(showOutput ? (action + EOL) : action);
            final ProcessBuilder builder = new ProcessBuilder();
            final List<String> command = new ArrayList<>();

            command.add(javaPath().toString());
            command.add(EXIT_ON_STARTED);
            command.addAll(Arrays.asList(jvmArgs));
            command.add(targetOption);
            command.add(target);
            builder.command(command);

            final Process process = builder.start();
            final StreamConsumer out = new StreamConsumer(process.getInputStream(), showOutput);
            final StreamConsumer err = new StreamConsumer(process.getErrorStream(), showOutput);
            final Future outFuture = EXECUTOR.submit(out);
            final Future errFuture = EXECUTOR.submit(err);
            int exitCode = process.waitFor();
            outFuture.cancel(true);
            errFuture.cancel(true);
            if (exitCode != 0) {
                final String message = action + " FAILED.";
                LOG.warn(message);
                if (!showOutput) {
                    dump(out, "out");
                    dump(err, "err");
                }
                throw new IllegalStateException(message);
            }
        }

        private static void dump(StreamConsumer stream, String name) {
            final List<String> lines = stream.lines();
            if (!lines.isEmpty()) {
                LOG.info("--- process " + name + " ---");
                lines.forEach(System.out::println);
            }
        }

        private Path javaPath() {
            return assertFile(javaHome.resolve(JAVA_CMD_PATH));
        }

        private static Path tempFile(String suffix) throws IOException {
            final File file = File.createTempFile(FILE_PREFIX, suffix);
            file.deleteOnExit();
            return file.toPath();
        }

        private static Path assertDir(Path path) {
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException(path + " is not a directory");
            }
            return path;
        }

        private static Path assertJar(Path path) {
            final String fileName = assertFile(path).getFileName().toString();
            if (!fileName.endsWith(JAR_SUFFIX)) {
                throw new IllegalArgumentException(path + " is not a jar");
            }
            return path;
        }

        private static Path assertFile(Path path) {
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException(path + " is not a file");
            }
            return path;
        }
    }

    private static class StreamConsumer implements Runnable {
        private final InputStream inputStream;
        private final List<String> lines;
        private final boolean showOutput;

        StreamConsumer(InputStream inputStream, boolean showOutput) {
            this.inputStream = inputStream;
            this.lines = new ArrayList<>();
            this.showOutput = showOutput;
        }

        List<String> lines() {
            return lines;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                                                                  .forEach(line -> {
                                                                      if (showOutput) {
                                                                          System.out.println(line);
                                                                      }
                                                                      lines.add(line);
                                                                  });
        }
    }
}
