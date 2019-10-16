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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import io.helidon.jlink.logging.Log;

import jdk.tools.jlink.internal.Archive;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.UnsupportedVersion;

/**
 * CDI indexing.
 */
class CdiIndexing {
    private static final Log LOG = Log.getLog("cdi");
    private static final String BEANS_PATH = "META-INF/beans.xml";
    private static final String JANDEX_INDEX_PATH = "META-INF/jandex.idx";
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String MODULE_INFO_CLASS = "module-info.class";
    private final DelegatingArchive archive;
    private final boolean isBeansArchive;
    private final boolean hasIndex;
    private Index index;

    /**
     * Add an index if required.
     *
     * @param context The context.
     * @param archive The archive.
     * @return The index.
     */
    static Index indexIfBeanArchive(ApplicationContext context, DelegatingArchive archive) {
        if (context.isMicroprofile()) {
            final CdiIndexing indexing = new CdiIndexing(archive);
            if (indexing.isBeansArchive()) {
                LOG.info(" contains CDI beans and %s indexed", indexing.containsIndex() ? "is" : "is not");
                return indexing.ensureIndex();
            }
        }
        return null;
    }

    private CdiIndexing(DelegatingArchive archive) {
        this.archive = archive;
        this.isBeansArchive = hasEntry(BEANS_PATH);
        this.hasIndex = isBeansArchive && hasEntry(JANDEX_INDEX_PATH);
    }

    private boolean isBeansArchive() {
        return isBeansArchive;
    }

    private boolean containsIndex() {
        return hasIndex;
    }

    private Index ensureIndex() {
        if (isBeansArchive) {
            if (hasIndex) {
                LOG.info(" loading Jandex index");
                loadIndex();
            } else {
                LOG.info(" adding Jandex index");
                buildIndex();
                addIndex();
            }
        }
        return index;
    }

    private void loadIndex() {
        try (InputStream in = getEntry(JANDEX_INDEX_PATH).stream()) {
            index = new IndexReader(in).read();
        } catch (IllegalArgumentException e) {
            LOG.warn("Jandex index in module %s is not valid: %s", archive.moduleName(), e.getMessage());
        } catch (UnsupportedVersion e) {
            LOG.warn("Jandex index in module %s is an unsupported version: %s", archive.moduleName(), e.getMessage());
        } catch (IOException e) {
            LOG.warn("Jandex index in module %s cannot be read: %s", archive.moduleName(), e.getMessage());
        }
    }

    private void addIndex() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final IndexWriter writer = new IndexWriter(out);
        try {
            writer.write(index);
            archive.addEntry(new ByteArrayArchiveEntry(archive, JANDEX_INDEX_PATH, out.toByteArray()));
        } catch (IOException e) {
            LOG.warn("Unable to add index: %s", e);
        }
    }

    private void buildIndex() {
        final String moduleName = archive.moduleName();
        LOG.info("Building Jandex index for module %s", moduleName);
        final Indexer indexer = new Indexer();
        classEntries().forEach(entry -> {
            try {
                indexer.index(entry.stream());
            } catch (IOException e) {
                LOG.warn("Could not index class %s in module %s: %s", entry.name(), moduleName, e.getMessage());
            }
        });
        this.index = indexer.complete();
    }

    private boolean hasEntry(String path) {
        return archive.entries().anyMatch(entry -> entry.name().equals(path));
    }

    private Archive.Entry getEntry(String path) {
        return archive.entries()
                      .filter(entry -> entry.name().equals(path))
                      .findFirst()
                      .orElseThrow(() -> new IllegalStateException("Could not get '" + path + "' entry."));
    }

    private Stream<Archive.Entry> classEntries() {
        return archive.entries()
                      .filter(entry -> {
                          final String name = entry.name();
                          return name.endsWith(CLASS_FILE_SUFFIX) && !name.equals(MODULE_INFO_CLASS);
                      });
    }
}
