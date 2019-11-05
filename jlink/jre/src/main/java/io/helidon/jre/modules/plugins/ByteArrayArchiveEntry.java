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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import jdk.tools.jlink.internal.Archive;

import static jdk.tools.jlink.internal.Archive.Entry.EntryType.CLASS_OR_RESOURCE;

/**
 * An Archive.Entry over a byte array.
 */
class ByteArrayArchiveEntry extends Archive.Entry {
    private final byte[] content;

    /**
     * Constructor.
     *
     * @param archive The enclosing archive.
     * @param name The entry name/path.
     * @param data The entry content.
     */
    ByteArrayArchiveEntry(Archive archive, String name, byte[] data) {
        super(archive, name, name, CLASS_OR_RESOURCE);
        this.content = data;
    }

    @Override
    public long size() {
        return content.length;
    }

    @Override
    public InputStream stream() {
        return new ByteArrayInputStream(content);
    }
}
