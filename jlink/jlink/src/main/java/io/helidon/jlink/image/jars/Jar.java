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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.StreamUtils;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.UnsupportedVersion;

import static io.helidon.jlink.common.util.FileUtils.assertDir;
import static io.helidon.jlink.common.util.FileUtils.assertFile;

/**
 * CDI BeansArchive aware Jar. Supports creating an index if missing and adding it during copy.
 */
public class Jar {
    private static final Log LOG = Log.getLog("jar");
    private static final String BEANS_PATH = "META-INF/beans.xml";
    private static final String JANDEX_INDEX_PATH = "META-INF/jandex.idx";
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String MODULE_INFO_CLASS = "module-info.class";
    private final Path path;
    private final JarFile jar;
    private final boolean isMultiRelease;
    private final boolean isBeansArchive;
    private final Index index;
    private final boolean builtIndex;
    private final ModuleDescriptor descriptor;

    public class Entry extends JarEntry {

        private Entry(JarEntry entry) {
            super(entry);
        }

        public String path() {
            return getName();
        }

        public InputStream data() {
            try {
                return jar.getInputStream(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public Jar(Path path) {
        final String fileName = assertFile(path).getFileName().toString();
        this.path = path;
        try {
            this.jar = new JarFile(path.toFile());
            this.isMultiRelease = isMultiRelease(jar.getManifest());
            this.isBeansArchive = hasEntry(BEANS_PATH);
            final boolean hasIndex = isBeansArchive && hasEntry(JANDEX_INDEX_PATH);
            Index index = null;
            boolean builtIndex = false;
            ModuleDescriptor descriptor = null;

            if (isBeansArchive) {
                if (hasIndex) {
                    index = loadIndex();
                }
                if (index == null) {
                    index = buildIndex();
                    builtIndex = true;
                }
            }

            final Entry moduleInfo = findEntry(MODULE_INFO_CLASS);
            if (moduleInfo != null) {
                descriptor = ModuleDescriptor.read(moduleInfo.data());
            }

            this.index = index;
            this.builtIndex = builtIndex;
            this.descriptor = descriptor;

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String name() {
        return path.getFileName().toString();
    }

    public Path path() {
        return path;
    }

    public Stream<Entry> entries() {
        final Iterator<JarEntry> iterator = jar.entries().asIterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                            .map(Entry::new);
    }

    public boolean isMultiRelease() {
        return isMultiRelease;
    }

    public boolean isBeansArchive() {
        return isBeansArchive;
    }

    public boolean hasIndex() {
        return isBeansArchive && index != null;
    }

    public boolean hasModuleDescriptor() {
        return descriptor != null;
    }

    public ModuleDescriptor moduleDescriptor() {
        return descriptor;
    }

    public Path copyToDirectory(Path dir) {
        final Path fileName = path.getFileName();
        final Path targetFile = assertDir(dir).resolve(fileName);
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(targetFile))) {

            // Add an index if we created it, otherwise just copy the file

            if (builtIndex) {
                copyAndAddIndex(out);
            } else {
                StreamUtils.copy(Files.newInputStream(path), out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return targetFile;
    }

    private Index loadIndex() {
        LOG.debug("Loading Jandex index for %s", name());
        try (InputStream in = getEntry(JANDEX_INDEX_PATH).data()) {
            return new IndexReader(in).read();
        } catch (IllegalArgumentException e) {
            LOG.warn("Jandex index in %s is not valid: %s", path, e.getMessage());
        } catch (UnsupportedVersion e) {
            LOG.warn("Jandex index in %s is an unsupported version: %s", path, e.getMessage());
        } catch (IOException e) {
            LOG.warn("Jandex index in %s cannot be read: %s", path, e.getMessage());
        }
        return null;
    }

    private Index buildIndex() {
        LOG.info("Building Jandex index for %s", name());
        final Indexer indexer = new Indexer();
        classEntries().forEach(entry -> {
            try {
                indexer.index(entry.data());
            } catch (IOException e) {
                LOG.warn("Could not index class %s in %s: %s", entry.path(), name(), e.getMessage());
            }
        });
        return indexer.complete();
    }

    private boolean hasEntry(String path) {
        return entries().anyMatch(entry -> entry.path().equals(path));
    }

    private Entry findEntry(String path) {
        return entries().filter(entry -> entry.path().equals(path))
                        .findFirst().orElse(null);
    }

    private Entry getEntry(String path) {
        return entries().filter(entry -> entry.path().equals(path))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Could not get '" + path + "' entry."));
    }

    private Stream<Entry> classEntries() {
        return entries().filter(entry -> {
            final String name = entry.path();
            return name.endsWith(CLASS_FILE_SUFFIX) && !name.equals(MODULE_INFO_CLASS);
        });
    }

    private void copyAndAddIndex(OutputStream out) throws IOException {
        try (final JarOutputStream jar = new JarOutputStream(out)) {

            // Add the index

            addIndex(jar);

            // Copy all entries, filtering out any previous index (that could not be read)

            entries().filter(e -> !e.path().equals(JANDEX_INDEX_PATH))
                     .forEach(entry -> {
                         try {
                             jar.putNextEntry(new JarEntry(entry.getName()));
                             if (!entry.isDirectory()) {
                                 StreamUtils.copy(entry.data(), jar);
                             }
                             jar.flush();
                             jar.closeEntry();
                         } catch (IOException e) {
                             throw new UncheckedIOException(e);
                         }
                     });
        }
    }

    private void addIndex(JarOutputStream jar) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final IndexWriter writer = new IndexWriter(out);
        try {
            writer.write(index);
            final ByteArrayInputStream data = new ByteArrayInputStream(out.toByteArray());
            jar.putNextEntry(new JarEntry(JANDEX_INDEX_PATH));
            StreamUtils.copy(data, jar);
            jar.flush();
            jar.closeEntry();
        } catch (IOException e) {
            LOG.warn("Unable to add index: %s", e);
        }
    }


    private static boolean isMultiRelease(Manifest manifest) {
        return "true".equalsIgnoreCase(mainAttribute(manifest, Attributes.Name.MULTI_RELEASE));
    }

    private static String mainAttribute(Manifest manifest, Attributes.Name name) {
        if (manifest != null) {
            final Object value = manifest.getMainAttributes().get(name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

}
