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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.Archive;

/**
 * An Archive wrapper base class.
 */
public abstract class DelegatingArchive implements Archive, Comparable<DelegatingArchive> {
    private final Archive delegate;
    private final Set<String> javaModuleNames;
    private ModuleDescriptor descriptor;
    private final Map<String, Entry> extraEntries = new HashMap<>();
    private Predicate<Entry> entryFilter = entry -> true;

    /**
     * Constructor.
     *
     * @param delegate The delegate.
     * @param descriptor The descriptor.
     * @param javaModuleNames The names of all Java modules.
     */
    DelegatingArchive(Archive delegate, ModuleDescriptor descriptor, Set<String> javaModuleNames) {
        this.delegate = delegate;
        this.descriptor = descriptor;
        this.javaModuleNames = javaModuleNames;
    }

    @Override
    public int compareTo(DelegatingArchive o) {
        if (moduleName().equals("java.base")) {
            return -1;
        } else if (o.moduleName().equals("java.base")) {
            return 1;
        } else {
            return moduleName().compareTo(o.moduleName());
        }
    }

    @Override
    public String toString() {
        return moduleName();
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
    public final Stream<Entry> entries() {
        return Stream.concat(delegate.entries()
                                     .filter(entryFilter)
                                     .filter(e -> !extraEntries.containsKey(e.name())),
                             extraEntries.values()
                                         .stream());
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
     * Returns the delegate.
     *
     * @return The delegate.
     */
    Archive delegate() {
        return delegate;
    }

    /**
     * Returns all Java module names.
     *
     * @return The names.
     */
    Set<String> javaModuleNames() {
        return javaModuleNames;
    }

    /**
     * Returns the set of Java module dependencies.
     *
     * @return The set.
     */
    public abstract Set<String> javaModuleDependencies();

    /**
     * Returns whether or not this is an automatic module.
     *
     * @return {@code true} if automatic.
     */
    public abstract boolean isAutomatic();

    void updateRequires(Map<String, String> substituteRequires) {
        final String moduleName = moduleName();
        final ModuleDescriptor current = descriptor();
        if (needsUpdate(current.requires(), substituteRequires)) {

            final ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(moduleName, current.modifiers());

            if (current.mainClass().isPresent()) {
                builder.mainClass(current.mainClass().get());
            }

            current.provides().forEach(builder::provides);

            builder.packages(current.packages());

            if (current.version().isPresent()) {
                builder.version(current.version().get());
            }

            current.requires().forEach(r -> {
                final String name = r.name();
                final String substitute = substituteRequires.get(name);
                if (substitute == null) {
                    builder.requires(r);
                } else if (substitute.equals(moduleName)) {
                    // Drop it.
                    System.out.println("Dropping requires " + name + " from " + moduleName);
                } else {
                    if (r.compiledVersion().isPresent()) {
                        builder.requires(r.modifiers(), substitute, r.compiledVersion().get());
                    } else {
                        builder.requires(r.modifiers(), substitute);
                    }
                }
            });

            current.exports().forEach(builder::exports);
            current.opens().forEach(builder::opens);
            current.uses().forEach(builder::uses);

            descriptor(builder.build());
        }
    }

    protected void descriptor(ModuleDescriptor descriptor) {
        this.descriptor = descriptor;
        addEntry(new ModuleInfoArchiveEntry(this, descriptor));
    }

    protected void entryFilter(Predicate<Archive.Entry> filter) {
        this.entryFilter = filter;
    }

    protected void addEntry(Archive.Entry entry) {
        extraEntries.put(entry.name(), entry);
    }

    private static boolean needsUpdate(Set<ModuleDescriptor.Requires> requires, Map<String, String> substituteRequires) {
        for (ModuleDescriptor.Requires require : requires) {
            if (substituteRequires.containsKey(require.name())) {
                return true;
            }
        }
        return false;
    }
}
