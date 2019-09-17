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
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.jlink.logging.Log;

import jdk.tools.jlink.internal.Archive;

/**
 * An archive representing a non-automatic module.
 */
public class ModuleArchive extends DelegatingArchive {
    private static final Log LOG = Log.getLog("module-archive");
    private final Set<String> jdkDependencies;

    /**
     * Constructor.
     *
     * @param delegate The delegate.
     * @param javaModuleNames The names of all Java modules.
     */
    public ModuleArchive(Archive delegate, ModuleDescriptor descriptor, Set<String> javaModuleNames) {
        super(delegate, descriptor, javaModuleNames);
        this.jdkDependencies = collectJdkDependencies();
        LOG.info("        JDK dependencies: %s", jdkDependencies);
    }

    @Override
    public Set<String> javaModuleDependencies() {
        return jdkDependencies;
    }

    private Set<String> collectJdkDependencies() {
        final Set<String> jdkModules = javaModuleNames();
        final ModuleDescriptor descriptor = descriptor();
        return descriptor.requires()
                         .stream()
                         .filter(r -> jdkModules.contains(r.name()))
                         .map(ModuleDescriptor.Requires::name)
                         .collect(Collectors.toSet());
    }
}
