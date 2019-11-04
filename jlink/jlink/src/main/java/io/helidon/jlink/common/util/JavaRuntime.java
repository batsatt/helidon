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
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;
import static io.helidon.jlink.common.util.FileUtils.assertDir;
import static io.helidon.jlink.common.util.FileUtils.assertFile;
import static io.helidon.jlink.common.util.FileUtils.listFiles;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A Java Runtime directory.
 */
public class JavaRuntime {
    private static final String JMODS_DIR = "jmods";
    private static final String JMOD_SUFFIX = ".jmod";
    private static final String JAVA_BASE_JMOD = "java.base.jmod";
    private static final String JMOD_MODULE_INFO_PATH = "classes/module-info.class";
    private static final String JRE_SUFFIX = "-jre";
    private static final String FILE_SEP = File.separator;
    private static final String JAVA_CMD_PATH = "bin" + FILE_SEP + "java";
    private final Path javaHome;
    private final Runtime.Version version;
    private final Path jmodsDir;
    private final List<Path> jmodFiles;
    private final Map<String, Path> modules;

    /**
     * Ensures a valid JRE directory path, deleting if required.
     *
     * @param jreDirectory The JRE directory. May be {@code null}.
     * @param mainJar The main jar, used to create a name if {@code jreDirectory} not provided.
     * May not be {@code null}.
     * @param replaceExisting {@code true} if the directory can be deleted if already present.
     * @return The directory.
     * @throws IOException If an error occurs.
     */
    public static Path prepareJreDirectory(Path jreDirectory, Path mainJar, boolean replaceExisting) throws IOException {
        if (jreDirectory == null) {
            final String jarName = requireNonNull(mainJar).getFileName().toString();
            final String dirName = jarName.substring(0, jarName.lastIndexOf('.')) + JRE_SUFFIX;
            jreDirectory = FileUtils.CURRENT_DIR.resolve(dirName);
        }
        if (Files.exists(jreDirectory)) {
            if (Files.isDirectory(jreDirectory)) {
                if (replaceExisting) {
                    FileUtils.deleteDirectory(jreDirectory);
                } else {
                    throw new IllegalArgumentException(jreDirectory + " is an existing directory");
                }
            } else {
                throw new IllegalArgumentException(jreDirectory + " is an existing file");
            }
        }
        return jreDirectory;
    }

    public static Path javaCommand(Path jreDir) {
        return assertFile(assertDir(jreDir).resolve(JAVA_CMD_PATH));
    }

    public static JavaRuntime current(boolean assertJdk) {
        return new JavaRuntime(CURRENT_JAVA_HOME_DIR, null, assertJdk);
    }

    public static JavaRuntime jdk(Path jdkDir) {
        return new JavaRuntime(jdkDir, null, true);
    }

    public static JavaRuntime jdk(Path jdkDir, Runtime.Version version) {
        return new JavaRuntime(jdkDir, version, true);
    }

    public static JavaRuntime jre(Path jreDir, Runtime.Version version) {
        return new JavaRuntime(jreDir, requireNonNull(version), false);
    }

    private JavaRuntime(Path javaHome, Runtime.Version version, boolean assertJdk) {
        javaCommand(javaHome); // Assert valid.
        this.javaHome = assertDir(javaHome);
        this.jmodsDir = javaHome.resolve(JMODS_DIR);
        if (Files.isDirectory(jmodsDir)) {
            this.jmodFiles = listFiles(jmodsDir, fileName -> fileName.endsWith(JMOD_SUFFIX));
            this.version = isCurrent() ? Runtime.version() : findVersion();
            this.modules = jmodFiles.stream()
                                    .collect(Collectors.toMap(JavaRuntime::moduleNameOf, identity()));
        } else if (version == null) {
            throw new IllegalArgumentException("Version required in a Java home without 'jmods' dir: " + javaHome);
        } else {
            this.version = version;
            this.jmodFiles = List.of();
            this.modules = Map.of();
        }
        if (assertJdk) {
            assertHasJmodFiles();
        }
    }

    public Runtime.Version version() {
        return version;
    }

    public String featureVersion() {
        return Integer.toString(version.feature());
    }

    public Path path() {
        return javaHome;
    }

    public boolean isCurrent() {
        return javaHome.equals(CURRENT_JAVA_HOME_DIR);
    }

    private void assertHasJmodFiles() {
        if (jmodFiles.isEmpty()) {
            throw new IllegalArgumentException("Not a JDK (no .jmod files found): " + javaHome);
        }
    }

    public Set<String> moduleNames() {
        return modules.keySet();
    }

    public Path jmodFile(String moduleName) {
        final Path result = modules.get(moduleName);
        if (result == null) {
            throw new IllegalStateException("Cannot find .jmod file for module '" + moduleName + "' in " + path());
        }
        return result;
    }

    /**
     * Ensure that the given directory exists, creating it if necessary.
     *
     * @param directory The directory. May be relative or absolute.
     * @return The directory.
     * @throws IllegalArgumentException If the directory is absolute but is not within this Java Home directory.
     */
    public Path ensureDirectory(Path directory) {
        Path relativeDir = requireNonNull(directory);
        if (directory.isAbsolute()) {
            // Ensure that the directory is within our directory.
            relativeDir = path().relativize(directory);
        }
        return FileUtils.ensureDirectory(path().resolve(relativeDir));
    }

    private Runtime.Version findVersion() {
        assertHasJmodFiles();
        final Path javaBase = assertFile(jmodsDir.resolve(JAVA_BASE_JMOD));
        try {
            final ZipFile zip = new ZipFile(javaBase.toFile());
            final ZipEntry entry = zip.getEntry(JMOD_MODULE_INFO_PATH);
            if (entry == null) {
                throw new IllegalStateException("Cannot find " + JMOD_MODULE_INFO_PATH + " in " + javaBase);
            }
            final ModuleDescriptor descriptor = ModuleDescriptor.read(zip.getInputStream(entry));
            return Runtime.Version.parse(descriptor.version()
                                                   .orElseThrow(() -> new IllegalStateException("No version in " + javaBase))
                                                   .toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String moduleNameOf(Path jmodFile) {
        final String fileName = jmodFile.getFileName().toString();
        return fileName.substring(0, fileName.length() - JMOD_SUFFIX.length());
    }
}
