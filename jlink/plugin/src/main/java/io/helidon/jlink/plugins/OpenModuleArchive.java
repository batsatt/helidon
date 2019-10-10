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

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.Archive;

import static io.helidon.jlink.plugins.ModuleDescriptors.convertToOpen;

/**
 * An archive representing a non-automatic but open module. If the underlying module is not open,
 * the descriptor will be re-written to make it so.
 */
public class OpenModuleArchive extends DelegatingArchive {

    /**
     * Constructor.
     *
     * @param delegate The delegate.
     * @param version The version.
     * @param allJdkModules The names of all JDK modules.
     */
    OpenModuleArchive(Archive delegate, ModuleDescriptor descriptor, Runtime.Version version, Set<String> allJdkModules) {
        super(delegate, descriptor, version, allJdkModules);
    }

    @Override
    public boolean isAutomatic() {
        return false;
    }

    @Override
    protected Set<String> collectDependencies(Map<String, DelegatingArchive> appArchivesByExport, Path javaHome) {
        return descriptor().requires()
                           .stream()
                           .map(ModuleDescriptor.Requires::name)
                           .collect(Collectors.toSet());
    }

    @Override
    protected ModuleDescriptor updateDescriptor(ModuleDescriptor descriptor) {
        if (descriptor.isOpen()) {
            return descriptor;
        } else {
            return convertToOpen(descriptor);
        }
    }
}
