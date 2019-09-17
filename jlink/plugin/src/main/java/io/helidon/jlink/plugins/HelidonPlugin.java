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
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.jlink.logging.Log;

import jdk.internal.module.ModulePath;
import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.internal.DirArchive;
import jdk.tools.jlink.internal.JmodArchive;
import jdk.tools.jlink.internal.ModularJarArchive;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * TODO: Describe
 */
public class HelidonPlugin implements Plugin {
    public static final String NAME = "helidon";
    public static final String JMOD_DIR_KEY = "jmodDir";
    public static final String JMOD_OVERRIDES_DIR_KEY = "jmodOverridesDir";
    private static final Log LOG = Log.getLog(NAME);

    private String appModuleName;
    private Path appModulePath;
    private Path appLibsDir;
    private Path jmodDir;
    private Path jmodOverridesDir;
    private Map<String, ModuleReference> javaModules;
    private Set<String> javaModuleNames;
    private Runtime.Version javaBaseVersion;
    private ModuleReference appModule;
    private Collection<ModuleReference> appLibModules;
    private DelegatingArchive appArchive;
    private List<DelegatingArchive> appArchives;
    private List<DelegatingArchive> javaArchives;
    private List<DelegatingArchive> allArchives;
    private Set<String> javaDependencies;

    /**
     * Constructor.
     */
    public HelidonPlugin() {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        appModulePath = configPath(NAME, config);
        appLibsDir = appModulePath.getParent().resolve("libs"); // Asserted valid in Main
        jmodDir = configPath(JMOD_DIR_KEY, config);
        jmodOverridesDir = configPath(JMOD_OVERRIDES_DIR_KEY, config);
        javaModules = javaModules(jmodDir, jmodOverridesDir);
        javaModuleNames = javaModules.keySet();
        javaBaseVersion = toRuntimeVersion(javaModules.get("java.base"));
        appModuleName = getModuleName(appModulePath);
        appModule = ModuleFinder.of(appModulePath).find(appModuleName).orElseThrow();
        appLibModules = toModules(appLibsDir, false);
        LOG.info("appModuleName: %s\n" +
                 "appModulePath: %s\n" +
                 "   appLibsDir: %s", appModuleName, appModulePath, appLibsDir);
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        try {
            LOG.info("Collecting application archives");
            collectAppArchives();
            javaDependencies = appArchives.stream()
                                          .flatMap(a -> a.javaModuleDependencies().stream())
                                          .collect(Collectors.toSet());
            LOG.info("Required Java modules: %s", javaDependencies);
            LOG.info("Collecting required Java archives");
            collectRequiredJavaArchives();
            appendArchives();
            LOG.info("Java Archives: %s", javaArchives);
            LOG.info(" App Archives: %s", appArchives);

/*
        in.moduleView().modules().forEach(m -> System.out.println("module: " + m.name()));

        module: helidon.jlink
        module: jdk.jdeps
        module: jdk.jlink
        module: java.compiler
        module: java.instrument
        module: jdk.internal.opt
        module: jdk.compiler
        module: java.base

        out is empty!
 */

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private Map<String, ModuleReference> javaModules(Path jmodDir, Path jmodOverridesDir) {
        final Map<String, ModuleReference> modules = toModulesMap(jmodDir, true);
        if (jmodOverridesDir == null) {
            return modules;
        } else {
            final Map<String, ModuleReference> overrides = toModulesMap(jmodOverridesDir, true);
            final Map<String, ModuleReference> merged = new HashMap<>();
            modules.forEach((moduleName, module) -> {
                final ModuleReference override = overrides.get(moduleName);
                if (override == null) {
                    merged.put(moduleName, module);
                } else {
                    final Runtime.Version overrideVersion = toRuntimeVersion(override);
                    final Runtime.Version overriddenVersion = toRuntimeVersion(module);
                    final int relation = overrideVersion.compareTo(overriddenVersion);
                    if (relation != 0) {
                        final String relationName = relation < 0 ? "older" : "newer";
                        LOG.warn("Overriding %s:%s with %s version: %s", moduleName,
                                 overriddenVersion, relationName, overrideVersion);
                    }
                    merged.put(moduleName, override);
                }
            });
            return merged;
        }
    }

    private Map<String, ModuleReference> toModulesMap(Path modulesDir, boolean jmods) {
        return toModules(modulesDir, jmods).stream()
                                           .collect(toMap(r -> r.descriptor().name(), r -> r));
    }

    private Collection<ModuleReference> toModules(Path modulesDir, boolean jmods) {
        final AtomicBoolean error = new AtomicBoolean();
        final Map<String, List<ModuleReference>> modules = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir)) {
            for (Path entry : stream) {
                try {
                    final Runtime.Version version = jmods ? Runtime.version() : javaBaseVersion;
                    ModulePath.of(version, jmods, entry).findAll().forEach(module -> {
                        final String moduleName = module.descriptor().name();
                        modules.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(module);
                    });
                } catch (FindException e) {
                    error.set(true);
                    LOG.warn(e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Weed out any duplicates and return

        return modules.values()
                      .stream()
                      .map(HelidonPlugin::select)
                      .collect(Collectors.toSet());
    }

    private static ModuleReference select(List<ModuleReference> duplicates) {
        if (duplicates.isEmpty()) {
            throw new IllegalStateException();
        } else if (duplicates.size() == 1) {
            return duplicates.get(0);
        } else {
            final Map<String, ModuleReference> fileNameToRef = duplicates.stream()
                                                                         .collect(toMap(HelidonPlugin::fileNameOf, r -> r));
            final String selectedFile = select(fileNameToRef.keySet());
            final ModuleReference selected = fileNameToRef.get(selectedFile);
            final List<Path> duplicateFiles = duplicates.stream()
                                                        .filter(r -> r != selected)
                                                        .map(r -> Paths.get(r.location().orElseThrow().getPath()).getFileName())
                                                        .collect(toList());
            LOG.warn("Duplicate '%s' modules: selected %s, ignoring %s", selected.descriptor().name(),
                     selectedFile, duplicateFiles);
            return selected;
        }
    }

    private static String select(Set<String> fileNames) {
        String selected = select("jakarta", fileNames);
        if (selected == null) {
            selected = select("javax", fileNames);
            if (selected == null) {
                // TODO: maybe find duplicate prefixes and select by version?
                final List<String> ordered = new ArrayList<>(fileNames);
                ordered.sort(null);
                selected = ordered.get(0);
            }
        }
        return selected;
    }

    private static String select(String prefix, Set<String> fileNames) {
        return select(fileName -> fileName.startsWith(prefix), fileNames);
    }

    private static String select(Predicate<String> filter, Set<String> fileNames) {
        final List<String> candidates = fileNames.stream().filter(filter).sorted().collect(toList());
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            final String selection = select(fileName -> !fileName.contains("-api-"), fileNames);
            return selection == null ? candidates.get(0) : selection;
        }
    }

    private static String fileNameOf(ModuleReference reference) {
        return Paths.get(reference.location().orElseThrow().getPath()).getFileName().toString();
    }

    private void collectAppArchives() {
        appArchives = new ArrayList<>();
        appArchive = toArchive(appModule);
        appArchives.add(appArchive);
        appLibModules.forEach(module -> appArchives.add(toArchive(module)));
    }

    private void collectRequiredJavaArchives() {
        javaArchives = new ArrayList<>();
        javaDependencies.forEach(javaModuleName -> {
            final DelegatingArchive javaArchive = toArchive(javaModules.get(javaModuleName));
            javaArchives.add(javaArchive);
        });
    }

    private void appendArchives() {
        javaArchives.sort(null);
        appArchives.sort(null);
        allArchives = new ArrayList<>(javaArchives);
        allArchives.addAll(appArchives);
    }

    private DelegatingArchive toArchive(ModuleReference reference) {
        final ModuleDescriptor descriptor = reference.descriptor();
        final String moduleName = descriptor.name();
        final boolean automatic = descriptor.isAutomatic();
        final Path modulePath = Paths.get(reference.location().orElseThrow().getPath());
        final String fileName = modulePath.getFileName().toString();
        final Runtime.Version version = versionOf(descriptor);

        LOG.info("Processing %smodule '%s:%s' at %s", automatic ? "automatic " : "", moduleName, version, modulePath);

        Archive archive;
        if (Files.isDirectory(modulePath)) {
            archive = new DirArchive(modulePath, moduleName);
        } else if (fileName.endsWith(".jar")) {
            archive = new ModularJarArchive(moduleName, modulePath, version);
        } else if (fileName.endsWith(".jmod")) {
            archive = new JmodArchive(moduleName, modulePath);
        } else {
            throw illegalArg("Unsupported module type: " + modulePath);
        }
        return automatic ? new AutomaticArchive(archive, descriptor, javaModuleNames, version, javaBaseVersion)
                         : new ModuleArchive(archive, descriptor, javaModuleNames);
    }

    private Runtime.Version versionOf(ModuleDescriptor descriptor) {
        final Optional<ModuleDescriptor.Version> version = descriptor.version();
        return descriptor.version().isPresent() ? toRuntimeVersion(version.get().toString()) : javaBaseVersion;
    }

    private Runtime.Version toRuntimeVersion(ModuleReference module) {
        return toRuntimeVersion(module.descriptor().version().orElseThrow().toString());
    }

    private Runtime.Version toRuntimeVersion(String version) {
        try {
            return Runtime.Version.parse(version);
        } catch (Exception e) {
            if (version.endsWith(".0")) {
                return toRuntimeVersion(version.substring(0, version.length() - 2));
            } else {
                LOG.debug("Cannot parse version '%s', using JDK version '%s'", version, javaBaseVersion);
                return javaBaseVersion;
            }
        }
    }

    private static String getModuleName(Path modulePath) {
        ModuleFinder finder = ModuleFinder.of(modulePath);
        Set<ModuleReference> modules = finder.findAll();
        if (modules.size() == 1) {
            return modules.iterator().next().descriptor().name();
        } else {
            throw new IllegalArgumentException(modulePath + " does not point to a module");
        }
    }

    private static Path configPath(String key, Map<String, String> config) {
        final String value = config.get(key);
        return value == null ? null : Paths.get(value);
    }

    private static IllegalArgumentException illegalArg(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalStateException illegalState(String message) {
        return new IllegalStateException(message);
    }
}
