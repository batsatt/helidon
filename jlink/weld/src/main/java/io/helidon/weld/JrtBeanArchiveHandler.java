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

package io.helidon.weld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.annotation.Priority;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.UnsupportedVersion;
import org.jboss.weld.environment.deployment.discovery.BeanArchiveBuilder;
import org.jboss.weld.environment.deployment.discovery.BeanArchiveHandler;
import org.jboss.weld.environment.deployment.discovery.jandex.Jandex;
import org.jboss.weld.environment.logging.CommonLogger;

import static io.helidon.weld.JrtBeanArchiveScanner.JRT_URI_PREFIX;
import static io.helidon.weld.JrtDiscoveryStrategy.TOP_PRIORITY;

/**
 * A {@link BeanArchiveHandler} that handles "jrt:/modules/${path}" archive references and
 * uses Jandex indexes if present or archive scanning if not.
 */
@Priority(value = TOP_PRIORITY)
public class JrtBeanArchiveHandler implements BeanArchiveHandler {
    private static final String JANDEX_INDEX_PATH = "META-INF/jandex.idx";
    private static final String JRT_BASE_URI = JRT_URI_PREFIX + "/";
    private static final int JRI_URI_PREFIX_LENGTH = JRT_URI_PREFIX.length();
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String MODULE_INFO_CLASS = "module-info.class";
    private static final char PATH_SEP_CHAR = '/';
    private static final char CLASS_SEP_CHAR = '.';

    private final FileSystem jrtFileSystem;

    /**
     * Constructor.
     */
    JrtBeanArchiveHandler() {
        this.jrtFileSystem = FileSystems.getFileSystem(URI.create(JRT_BASE_URI));
        CommonLogger.LOG.tracef("%s active", getClass());
    }

    @Override
    public BeanArchiveBuilder handle(String jrtPath) {
        if (jrtPath.startsWith(JRT_URI_PREFIX)) {
            final Path moduleRoot = jrtFileSystem.getPath(jrtPath.substring(JRI_URI_PREFIX_LENGTH));
            final Path indexFile = indexFile(moduleRoot);
            BeanArchiveBuilder result = null;
            if (indexFile != null) {
                result = fromIndex(indexFile);
            }
            if (result == null) {
                result = fromImage(moduleRoot);
            }
            return result;
        } else {
            CommonLogger.LOG.tracef("%s does not support url format: %s", getClass(), jrtPath);
            return null;
        }
    }

    private Path indexFile(Path moduleRoot) {
        final Path indexFile = moduleRoot.resolve(JANDEX_INDEX_PATH);
        if (Files.exists(indexFile)) {
            return indexFile;
        } else {
            return null;
        }
    }

    private BeanArchiveBuilder fromImage(Path moduleRoot) {
        CommonLogger.LOG.tracef("%s scanning: %s", getClass(), moduleRoot);
        final BeanArchiveBuilder builder = new BeanArchiveBuilder();
        final int rootPrefixLength = moduleRoot.toString().length();
        try (Stream<Path> stream = Files.walk(moduleRoot, Integer.MAX_VALUE)) {
            stream.filter(path -> !path.equals(moduleRoot))
                  .filter(JrtBeanArchiveHandler::isClass)
                  .forEach(classFile -> {
                      final String className = toClassName(classFile, rootPrefixLength);
                      CommonLogger.LOG.tracef("   adding %s", className);
                      builder.addClass(className);
                  });
            return builder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BeanArchiveBuilder fromIndex(Path indexFile) {
        CommonLogger.LOG.tracef("%s loading index from %s", getClass(), indexFile);
        final Index index = index(indexFile);
        if (index == null) {
            return null;
        } else {
            CommonLogger.LOG.trace("  index loaded");
            final BeanArchiveBuilder builder = new BeanArchiveBuilder().setAttribute(Jandex.INDEX_ATTRIBUTE_NAME, index);
            for (ClassInfo classInfo : index.getKnownClasses()) {
                CommonLogger.LOG.tracef("  adding %s", classInfo.name());
                builder.addClass(classInfo.name().toString());
            }
            return builder;
        }
    }

    private Index index(Path indexFile) {
        try (InputStream in = Files.newInputStream(indexFile)) {
            return new IndexReader(in).read();
        } catch (IllegalArgumentException e) {
            CommonLogger.LOG.warnv("Jandex index is not valid: {0}", indexFile);
        } catch (UnsupportedVersion e) {
            CommonLogger.LOG.warnv("Version of Jandex index is not supported: {0}", indexFile);
        } catch (IOException e) {
            CommonLogger.LOG.warnv("Cannot get Jandex index from: {0}", indexFile);
            CommonLogger.LOG.catchingDebug(e);
        }
        return null;
    }

    private static boolean isClass(Path path) {
        final String fileName = path.getFileName().toString();
        return fileName.endsWith(CLASS_FILE_SUFFIX) && !fileName.equals(MODULE_INFO_CLASS);
    }

    private static String toClassName(Path path, int rootPrefixLength) {
        // classFile = {jdk.internal.jrtfs.JrtPath@2604} "/modules/jersey.weld2.se/module-info.class"
        // className = ".modules.jersey.weld2.se.module-info"
        final String file = path.toString().substring(rootPrefixLength + 1);
        return file.substring(0, file.lastIndexOf(CLASS_FILE_SUFFIX)).replace(PATH_SEP_CHAR, CLASS_SEP_CHAR);
    }
}
