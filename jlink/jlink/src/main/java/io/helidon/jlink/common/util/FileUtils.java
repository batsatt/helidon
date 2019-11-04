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
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * File utilities.
 */
public class FileUtils {
    public static final Path CURRENT_JAVA_HOME_DIR = Paths.get(System.getProperty("java.home"));
    public static final Path CURRENT_DIR = Paths.get(".").toAbsolutePath();

    public static Path ensureDirectory(Path path, FileAttribute<?>... attrs) {
        if (Files.exists(requireNonNull(path))) {
            return assertDir(path);
        } else {
            try {
                return Files.createDirectories(path, attrs);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * List all files in the given directory that match the given filter. Does not recurse.
     *
     * @param dir The directory.
     * @param fileNameFilter The filter.
     * @return The files.
     */
    public static List<Path> listFiles(Path dir, Predicate<String> fileNameFilter) {
        try {
            return Files.find(assertDir(dir), 1, (path, attrs) ->
                attrs.isRegularFile() && fileNameFilter.test(path.getFileName().toString())
            ).collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * List all files and directories in the given directory. Does not recurse.
     *
     * @param dir The directory.
     * @return The files.
     */
    public static List<Path> list(Path dir) {
        try {
            return Files.find(assertDir(dir), 1, (path, attrs) -> true)
                        .collect(Collectors.toList());
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
        if (Files.isDirectory(requireNonNull(dir))) {
            return dir.toAbsolutePath().normalize();
        } else {
            throw new IllegalArgumentException(dir + " is not a directory");
        }
    }

    public static Path assertFile(Path file) {
        if (Files.isRegularFile(requireNonNull(file))) {
            return file.toAbsolutePath().normalize();
        } else {
            throw new IllegalArgumentException(file + " is not a file");
        }
    }

    public static Path assertExists(Path path) {
        if (Files.exists(requireNonNull(path))) {
            return path.toAbsolutePath().normalize();
        } else {
            throw new IllegalArgumentException(path + " does not exist");
        }
    }

    public static Path deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (Files.isDirectory(directory)) {
                Files.walk(directory)
                     .sorted(Comparator.reverseOrder())
                     .forEach(file -> {
                         try {
                             Files.delete(file);
                         } catch (Exception e) {
                             throw new Error(e);
                         }
                     });
            } else {
                throw new IllegalArgumentException(directory + " is not a directory");
            }
        }
        return directory;
    }

    private FileUtils() {
    }
}
