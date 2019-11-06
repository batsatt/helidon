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

import java.lang.module.ModuleDescriptor;

import jdk.tools.jlink.internal.Archive;

import static io.helidon.jre.modules.plugins.ModuleDescriptors.compile;

/**
 * An Archive.Entry for a module-info.class.
 */
class ModuleInfoArchiveEntry extends ByteArrayArchiveEntry {
    private static final String NAME = "module-info.class";

    /**
     * Constructor.
     *
     * @param archive The enclosing archive.
     * @param descriptor The descriptor.
     */
    ModuleInfoArchiveEntry(Archive archive, ModuleDescriptor descriptor) {
        super(archive, NAME, compile(descriptor));
    }
}