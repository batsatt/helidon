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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.FileUtils;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;
import static io.helidon.jlink.common.util.FileUtils.assertDir;
import static io.helidon.jlink.common.util.FileUtils.assertFile;
import static io.helidon.jlink.common.util.FileUtils.listFiles;

/**
 * A Java installation directory.
 */
public class JavaHome {
    private static final Log LOG = Log.getLog("java-home");
    private static final String JMODS_DIR = "jmods";
    private static final String JMOD_SUFFIX = ".jmod";
    private static final String JAVA_BASE_JMOD = "java.base.jmod";
    private static final String JMOD_MODULE_INFO_PATH = "classes/module-info.class";
    private static final String APP_DIR = "app";
    private static final String APP_LIBS_DIR = "app/libs";
    private final Path javaHome;
    private final Runtime.Version version;
    private final Path jmodsDir;
    private final List<Path> jmodFiles;
    private final Set<String> moduleNames;

    public JavaHome() {
        this(CURRENT_JAVA_HOME_DIR);
    }

    public JavaHome(Path javaHome) {
        this(javaHome, null);
    }

    public JavaHome(Path javaHome, Runtime.Version version) {
        this.javaHome = assertDir(javaHome);
        this.jmodsDir = javaHome.resolve(JMODS_DIR);
        if (Files.isDirectory(jmodsDir)) {
            this.jmodFiles = listFiles(jmodsDir, fileName -> fileName.endsWith(JMOD_SUFFIX));

            this.version = isCurrent() ? Runtime.version() : findVersion();
            this.moduleNames = jmodFiles.stream()
                                        .map(path -> {
                                            final String fileName = path.getFileName().toString();
                                            return fileName.substring(0, fileName.length() - JMOD_SUFFIX.length());
                                        }).collect(Collectors.toSet());
        } else if (version == null) {
            throw new IllegalArgumentException("version required in a java home without jmods dir");
        } else {
            this.version = version;
            this.jmodFiles = List.of();
            this.moduleNames = Set.of();
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

    public JavaHome assertHasJmodFiles() {
        if (jmodFiles.isEmpty()) {
            throw new IllegalArgumentException(javaHome + " must contain jmod files");
        }
        return this;
    }

    public Set<String> moduleNames() {
        return moduleNames;
    }

    public Path applicationDir() {
        return FileUtils.ensureDirectory(path().resolve(APP_DIR));
    }

    public Path applicationLibsDir() {
        return FileUtils.ensureDirectory(path().resolve(APP_LIBS_DIR));
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
}
