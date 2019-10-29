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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * File utilities.
 */
public class FileUtils {

    public static List<Path> listJarFiles(Path dir) {
        return listFiles(dir, fileName -> fileName.endsWith(".jar"));
    }

    public static List<Path> listJmodFiles(Path dir) {
        return listFiles(dir, fileName -> fileName.endsWith(".jmod"));
    }

    public static Path ensureDirectory(Path path, FileAttribute<?>... attrs) {
        if (Files.exists(path)) {
            return assertDir(path);
        } else {
            try {
                return Files.createDirectories(path, attrs);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static List<Path> listFiles(Path dir, Predicate<String> fileNameFilter) {
        try {
            return Files.find(assertDir(dir), 1, (path, attrs) ->
                attrs.isRegularFile() && fileNameFilter.test(path.getFileName().toString())
            ).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path assertNonEmptyDir(Path dir) {
        Path result = assertDir(dir);
        try {
            if (Files.list(dir).noneMatch(Files::isRegularFile)) {
                throw new IllegalArgumentException("no files found in directory " + result);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path assertDir(Path dir) {
        if (Files.isDirectory(dir)) {
            return dir.toAbsolutePath().normalize();
        } else {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
    }

    public static Path assertExists(Path path) {
        if (Files.exists(path)) {
            return path.toAbsolutePath().normalize();
        } else {
            throw new IllegalArgumentException(path + " does not exist");
        }
    }

    private FileUtils() {
    }
}
