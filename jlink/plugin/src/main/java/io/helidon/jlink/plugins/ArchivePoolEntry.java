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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * A ResourcePoolEntry using an Archive.Entry.
 */
public class ArchivePoolEntry implements ResourcePoolEntry {
    private final String module;
    private final Archive.Entry entry;
    private final String path;
    private final Type type;

    /**
     * Constructor.
     *
     * @param module The module name.
     * @param entry The archive entry.
     */
    ArchivePoolEntry(String module, Archive.Entry entry) {
        this.module = module;
        this.entry = entry;
        this.path = entry.getResourcePoolEntryName();
        this.type = imageFileTypeOf(entry);
    }

    @Override
    public final String moduleName() {
        return module;
    }

    @Override
    public final String path() {
        return path;
    }

    @Override
    public final Type type() {
        return type;
    }

    @Override
    public InputStream content() {
        try {
            return entry.stream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public long contentLength() {
        return entry.size();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.path);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ArchivePoolEntry)) {
            return false;
        }
        ArchivePoolEntry f = (ArchivePoolEntry) other;
        return f.path.equals(path);
    }

    @Override
    public String toString() {
        return path();
    }

    private static ResourcePoolEntry.Type imageFileTypeOf(Archive.Entry entry) {
        switch (entry.type()) {
            case CLASS_OR_RESOURCE:
                return Type.CLASS_OR_RESOURCE;
            case CONFIG:
                return Type.CONFIG;
            case HEADER_FILE:
                return Type.HEADER_FILE;
            case LEGAL_NOTICE:
                return Type.LEGAL_NOTICE;
            case MAN_PAGE:
                return Type.MAN_PAGE;
            case NATIVE_CMD:
                return Type.NATIVE_CMD;
            case NATIVE_LIB:
                return Type.NATIVE_LIB;
            default:
                throw new IllegalArgumentException("Unknown archive entry type: " + entry.type());
        }
    }
}
