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
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.Archive;

/**
 * TODO: Describe
 */
public abstract class DelegatingArchive implements Archive {
    private static final AtomicReference<Set<String>> JDK_MODULES = new AtomicReference<>();
    private final Archive delegate;
    private final ModuleDescriptor descriptor;

    /**
     * Returns all JDK module names.
     *
     * @return The names.
     */
    static Set<String> jdkModuleNames() {
        if (JDK_MODULES.get() == null) {
            JDK_MODULES.set(ModuleFinder.ofSystem()
                                        .findAll()
                                        .stream()
                                        .map(r -> r.descriptor().name())
                                        .collect(Collectors.toSet()));
        }
        return JDK_MODULES.get();
    }

    /**
     * Constructor.
     *
     * @param delegate The delegate.
     * @param descriptor The descriptor.
     */
    DelegatingArchive(Archive delegate, ModuleDescriptor descriptor) {
        this.delegate = delegate;
        this.descriptor = descriptor;
    }

    /**
     * Returns the descriptor.
     *
     * @return The descriptor.
     */
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String moduleName() {
        return delegate.moduleName();
    }

    @Override
    public Path getPath() {
        return delegate.getPath();
    }

    @Override
    public Stream<Entry> entries() {
        return delegate.entries();
    }

    @Override
    public void open() throws IOException {
        delegate.open();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Returns the set of JDK dependencies.
     *
     * @return The set.
     */
    public abstract Set<String> jdkDependencies();
}
