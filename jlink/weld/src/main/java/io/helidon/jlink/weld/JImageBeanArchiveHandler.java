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

package io.helidon.jlink.weld;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.annotation.Priority;

import org.jboss.weld.environment.deployment.discovery.BeanArchiveBuilder;
import org.jboss.weld.environment.deployment.discovery.BeanArchiveHandler;

import static io.helidon.jlink.weld.JImageBeanArchiveScanner.JRT_URI_PREFIX;
import static io.helidon.jlink.weld.JImageDiscoveryStrategy.TOP_PRIORITY;

/**
 * A {@link BeanArchiveHandler} that handles "jrt:/" archive references.
 */
@Priority(value = TOP_PRIORITY)
public class JImageBeanArchiveHandler implements BeanArchiveHandler {
    private static final String JANDEX_INDEX_PATH = "META-INF/jandex.idx";
    private static final String JRT_BASE_URI = JRT_URI_PREFIX + "/";
    private static final int JRI_URI_PREFIX_LENGTH = JRT_URI_PREFIX.length();
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String MODULE_INFO_CLASS = "module-info.class";
    private static final char PATH_SEP_CHAR = '/';
    private static final char CLASS_SEP_CHAR = '.';

    private final FileSystem jrtFileSystem;

    JImageBeanArchiveHandler() {
        this.jrtFileSystem = FileSystems.getFileSystem(URI.create(JRT_BASE_URI));
        System.out.println("JImageBeanArchiveHandler ctor"); // TODO remove
    }

    @Override
    public BeanArchiveBuilder handle(String jrtPath) {
        if (jrtPath.startsWith(JRT_URI_PREFIX)) {
            final Path moduleRoot = jrtFileSystem.getPath(jrtPath.substring(JRI_URI_PREFIX_LENGTH));
            final Path index = index(moduleRoot);
            if (index != null) {
                return fromIndex(index);
            } else {
                return fromImage(moduleRoot);
            }
        } else {
            System.out.println("JImageBeanArchiveHandler: unsupported url " + jrtPath);
            return null;
        }
    }

    private Path index(Path moduleRoot) {
        final Path index = moduleRoot.resolve(JANDEX_INDEX_PATH);
        if (Files.exists(index)) {
            System.out.println("JImageBeanArchiveHandler found but ignoring index: " + index);
            return null; // TODO: fix when fromIndex() is implemented
        } else {
            return null;
        }
    }

    private BeanArchiveBuilder fromImage(Path moduleRoot) {
        System.out.println("JImageBeanArchiveHandler scanning: " + moduleRoot);
        final BeanArchiveBuilder builder = new BeanArchiveBuilder();
        final int rootPrefixLength = moduleRoot.toString().length();
        try (Stream<Path> stream = Files.walk(moduleRoot, Integer.MAX_VALUE)) {
            stream.filter(path -> !path.equals(moduleRoot))
                  .filter(JImageBeanArchiveHandler::isClass)
                  .forEach(classFile -> {
                      final String className = toClassName(classFile, rootPrefixLength);
                      System.out.println("  adding " + className);
                      builder.addClass(className);
                  });
            return builder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BeanArchiveBuilder fromIndex(Path index) {
        System.out.println("JImageBeanArchiveHandler loading index from: " + index);
        throw new UnsupportedOperationException(); // TODO
    }

    private static boolean isClass(Path path) {
        final String fileName =  path.getFileName().toString();
        return fileName.endsWith(CLASS_FILE_SUFFIX) && !fileName.equals(MODULE_INFO_CLASS);
    }

    private static String toClassName(Path path, int rootPrefixLength) {
        // classFile = {jdk.internal.jrtfs.JrtPath@2604} "/modules/jersey.weld2.se/module-info.class"
        // className = ".modules.jersey.weld2.se.module-info"
        final String file = path.toString().substring(rootPrefixLength + 1);
        return file.substring(0, file.lastIndexOf(CLASS_FILE_SUFFIX)).replace(PATH_SEP_CHAR, CLASS_SEP_CHAR);
    }
}
