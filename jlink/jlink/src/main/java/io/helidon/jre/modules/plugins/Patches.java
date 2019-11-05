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

package io.helidon.jre.modules.plugins;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.internal.ModularJarArchive;

import static java.util.Objects.requireNonNull;

/**
 * Collects entries from patch jars. The file name of patch jars must be the module name
 * that the entries are expected to override.
 */
class Patches {
    private static final String PATCH_JAR_SUFFIX = "-patch.jar";
    private final Path patchesDir;
    private final Runtime.Version version;

    Patches(Path patchesDir, Runtime.Version version) {
        this.patchesDir = requireNonNull(patchesDir);
        this.version = requireNonNull(version);
    }

    Stream<Archive.Entry> entries() {
        final List<Archive.Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(patchesDir)) {
            for (Path patchJar : stream) {
                final Archive archive = toArchive(patchJar, version);
                archive.entries().forEach(entries::add);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return entries.stream();
    }

    private static Archive toArchive(Path file, Runtime.Version version) {
        requireNonNull(file);
        requireNonNull(version);
        final String fileName = file.getFileName().toString();
        if (!fileName.endsWith(PATCH_JAR_SUFFIX)) {
            throw new UnsupportedOperationException("Unsupported format: " + fileName);
        }
        final String moduleName = fileName.substring(0, fileName.length() - PATCH_JAR_SUFFIX.length());
        return new ModularJarArchive(moduleName, file, version);
    }
}
