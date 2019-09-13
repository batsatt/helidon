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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.internal.DirArchive;
import jdk.tools.jlink.internal.ModularJarArchive;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

/**
 * TODO: Describe
 */
public class HelidonPlugin implements Plugin {
    private static final String NAME = "helidon";

    private String appModuleName;
    private Path appModulePath;
    private Path appLibsDir;
    private Runtime.Version javaBaseVersion;
    private ModuleReference appModule;
    private Collection<ModuleReference> appLibModules;
    private Archive appArchive;
    private List<Archive> allArchives;

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
        appModulePath = Paths.get(config.get(NAME));
        appLibsDir = appModulePath.getParent().resolve("libs");
        appModuleName = getModuleName(appModulePath);
        appModule = ModuleFinder.of(appModulePath).find(appModuleName).orElseThrow();
        appLibModules = toModules(appLibsDir);
        System.out.println("appModuleName: " + appModuleName);
        System.out.println("appModulePath: " + appModulePath);
        System.out.println("   appLibsDir: " + appModulePath);
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        javaBaseVersion = javaBaseVersion(in);
        collectArchives();


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
    }

    private static Collection<ModuleReference> toModules(Path appLibsDir) {
        final AtomicBoolean error = new AtomicBoolean();
        final Map<String, ModuleReference> modules = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(appLibsDir)) {
            for (Path entry : stream) {
                try {
                    ModuleFinder.of(entry).findAll().forEach(module -> {
                        final String moduleName = module.descriptor().name();
                        final ModuleReference existing = modules.get(moduleName);
                        if (existing == null) {
                            modules.put(moduleName, module);
                        } else {
                            error.set(true);
                            final Path currentFile = Paths.get(module.location().orElseThrow().getPath()).getFileName();
                            final Path existingFile = Paths.get(existing.location().orElseThrow().getPath()).getFileName();
                            System.out.println("Ignoring duplicate module '" + moduleName + "': " +
                                               currentFile.getFileName() + " and " + existingFile);
                        }
                    });
                } catch (FindException e) {
                    error.set(true);
                    System.out.println(e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

// TODO       if (error.get()) {
//            throw new FindException("errors loading library modules");
//        }
        return modules.values();
    }

    private void collectArchives() {
        appArchive = toArchive(appModule);
        allArchives = new ArrayList<>();
        allArchives.add(appArchive);
        appLibModules.forEach(module -> allArchives.add(toArchive(module)));
    }

    private Archive toArchive(ModuleReference reference) {
        final ModuleDescriptor descriptor = reference.descriptor();
        final String moduleName = descriptor.name();
        final Runtime.Version version = versionOf(descriptor);
        final boolean automatic = descriptor.isAutomatic();
        final Path modulePath = Paths.get(reference.location().orElseThrow().getPath());
        final String fileName = modulePath.getFileName().toString();

        Archive archive;
        if (Files.isDirectory(modulePath)) {
            archive = new DirArchive(modulePath, moduleName);
        } else if (fileName.endsWith(".jar")) {
            archive = new ModularJarArchive(moduleName, modulePath, version);
        } else {
            throw illegalArg("Unsupported module type: " + modulePath);
        }
        return automatic ? new AutomaticArchive(archive, version) : archive;
    }

    private Runtime.Version versionOf(ModuleDescriptor descriptor) {
        return descriptor.version().isPresent() ? toRuntimeVersion(descriptor.version().get())
                                                : javaBaseVersion;
    }

    private static Runtime.Version javaBaseVersion(ResourcePool in) {
        return toRuntimeVersion(in.moduleView()
                                  .findModule("java.base")
                                  .orElseThrow(() -> illegalState("java.base module not found"))
                                  .descriptor()
                                  .version().orElseThrow(() -> illegalState("No version in java.base descriptor")));
    }

    private static Runtime.Version toRuntimeVersion(ModuleDescriptor.Version version) {
        return Runtime.Version.parse(version.toString());
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

    private static IllegalArgumentException illegalArg(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalStateException illegalState(String message) {
        return new IllegalStateException(message);
    }
}
