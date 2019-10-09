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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.jlink.logging.Log;

import jdk.tools.jlink.internal.Archive;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * An Archive wrapper base class.
 */
public abstract class DelegatingArchive implements Archive, Comparable<DelegatingArchive> {
    private static final Log LOG = Log.getLog("delegating-archive");
    private final Archive delegate;
    private final Set<String> allJdkModules;
    private final Set<String> jdkDependencies;
    private final Set<String> dependencies;

    private ModuleDescriptor descriptor;
    private final Runtime.Version version;
    private final Map<String, Entry> extraEntries = new HashMap<>();
    private Predicate<Entry> entryFilter = entry -> true;

    /**
     * Constructor.
     *
     * @param delegate The delegate.
     * @param descriptor The descriptor.
     * @param version The version.
     * @param allJdkModules The names of all JDK modules.
     */
    DelegatingArchive(Archive delegate, ModuleDescriptor descriptor, Runtime.Version version, Set<String> allJdkModules) {
        this.delegate = delegate;
        this.descriptor = descriptor;
        this.version = version;
        this.allJdkModules = allJdkModules;
        this.dependencies = new HashSet<>();
        this.jdkDependencies = new HashSet<>();
    }

    final void prepare(Map<String, DelegatingArchive> appArchivesByExport) {

        // Create a copy of the map without any references to this archive

        final Map<String, DelegatingArchive> reduced = appArchivesByExport.entrySet()
                                                                          .stream()
                                                                          .filter(e -> !e.getValue().equals(this))
                                                                          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Collect dependencies

        LOG.info("Collecting dependencies of %s", this);

        this.dependencies.addAll(collectDependencies(reduced));

        // Collect the subset of jdk dependencies

        this.jdkDependencies.addAll(dependencies.stream()
                                                .filter(allJdkModules::contains)
                                                .collect(Collectors.toSet()));
        LOG.info("    Non-JDK dependencies: %s", dependencies.stream()
                                                             .filter(s -> !jdkDependencies.contains(s))
                                                             .sorted()
                                                             .collect(toList()));
        LOG.info("        JDK dependencies: %s", jdkDependencies.stream().sorted().collect(toList()));

        // Update the descriptor if needed

        final ModuleDescriptor newDescriptor = updateDescriptor(descriptor);
        if (newDescriptor != descriptor) {
            descriptor(newDescriptor);
        }
    }

    protected abstract Set<String> collectDependencies(Map<String, DelegatingArchive> appArchivesByExport);

    protected abstract ModuleDescriptor updateDescriptor(ModuleDescriptor descriptor);


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
        final StringBuilder sb = new StringBuilder();
        if (isAutomatic()) {
            sb.append("automatic ");
        }
        if (isOpen()) {
            sb.append("open ");
        }
        sb.append("module '").append(moduleName()).append('@').append(version()).append("'");
        sb.append(" at ").append(getPath());
        return sb.toString();
    }

    /**
     * Returns the descriptor.
     *
     * @return The descriptor.
     */
    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Returns the version.
     *
     * @return The version.
     */
    public Runtime.Version version() {
        return version;
    }

    public Set<String> dependencies() {
        return dependencies;
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
    Set<String> allJdkModules() {
        return allJdkModules;
    }

    /**
     * Returns the set of Java module dependencies.
     *
     * @return The set.
     */
    public Set<String> jdkDependencies() {
        return jdkDependencies;
    }

    /**
     * Returns whether or not this is an automatic module.
     *
     * @return {@code true} if automatic.
     */
    public abstract boolean isAutomatic();

    /**
     * Returns whether or not this is an open module.
     *
     * @return {@code true} if open.
     */
    public boolean isOpen() {
        return descriptor.isOpen();
    }

    // TODO: move to ModuleDescriptors
    void updateRequires(Map<String, String> substituteRequires, Set<String> extraRequires) {
        final String moduleName = moduleName();
        final ModuleDescriptor current = descriptor();
        if (needsUpdate(current.requires(), substituteRequires) ||
            needsUpdate(current.requires(), extraRequires)) {

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
                    LOG.info("Dropping requires " + name + " from " + moduleName);
                } else {
                    if (r.compiledVersion().isPresent()) {
                        builder.requires(r.modifiers(), substitute, r.compiledVersion().get());
                    } else {
                        builder.requires(r.modifiers(), substitute);
                    }
                }
            });

            final Set<String> existingRequires = current.requires()
                                                        .stream()
                                                        .map(ModuleDescriptor.Requires::name)
                                                        .collect(Collectors.toSet());
            final Set<String> allRequires = new HashSet<>(existingRequires);

            extraRequires.forEach(extra -> {
                final String substitute = substituteRequires.get(extra);
                final String module = substitute == null ? extra : substitute;
                if (!allRequires.contains(module)) {
                    if (!module.equals(moduleName)) {
                        builder.requires(module);
                        allRequires.add(module);
                    } else {
                        LOG.debug("Dropping requires " + module + " from " + moduleName);
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

    private static boolean needsUpdate(Set<ModuleDescriptor.Requires> requires, Set<String> extraRequires) {
        for (ModuleDescriptor.Requires require : requires) {
            if (!extraRequires.contains(require.name())) {
                return true;
            }
        }
        return false;
    }
}
