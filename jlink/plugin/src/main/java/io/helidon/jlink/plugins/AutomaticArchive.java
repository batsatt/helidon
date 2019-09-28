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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.spi.ToolProvider;

import io.helidon.jlink.logging.Log;

import jdk.tools.jlink.internal.Archive;

import static io.helidon.jlink.plugins.ModuleDescriptors.updateAutomaticDescriptor;
import static jdk.tools.jlink.internal.Archive.Entry.EntryType.CLASS_OR_RESOURCE;

/**
 * An archive representing an automatic module.
 */
public class AutomaticArchive extends DelegatingArchive {
    private static final Log LOG = Log.getLog("automatic-archive");
    private static final ToolProvider JDEPS = ToolProvider.findFirst("jdeps").orElseThrow();
    private final Runtime.Version version;
    private final Manifest manifest;
    private final boolean isMultiRelease;
    private final String releaseFeatureVersion;
    private final Set<String> jdkDependencies;
    private final Set<String> dependencies;

    /**
     * Constructor.
     *
     * @param delegate The delegate archive.
     * @param descriptor The descriptor.
     * @param javaModuleNames The names of all Java modules.
     * @param version The archive version.
     * @param jdkVersion The JDK version.
     */
    public AutomaticArchive(Archive delegate,
                            ModuleDescriptor descriptor,
                            Set<String> javaModuleNames,
                            Runtime.Version version,
                            Runtime.Version jdkVersion) {
        super(delegate, descriptor, javaModuleNames);
        this.version = version;
        this.manifest = manifest();
        this.isMultiRelease = "true".equalsIgnoreCase(mainAttribute(Attributes.Name.MULTI_RELEASE));
        this.releaseFeatureVersion = Integer.toString(jdkVersion.feature());
        this.jdkDependencies = new HashSet<>();
        this.dependencies = new HashSet<>();
        collectDependencies();
        LOG.info("   Multi release version: %s", isMultiRelease ? releaseFeatureVersion : "none");
        LOG.info("        JDK dependencies: %s", jdkDependencies);

        // Update the descriptor to export all packages and require all dependencies

        descriptor(updateAutomaticDescriptor(descriptor, dependencies));

        // Setup excluded packages filter if needed

        checkExcludedPackages(descriptor.name());
    }

    @Override
    public Set<String> javaModuleDependencies() {
        return jdkDependencies;
    }

    @Override
    public boolean isAutomatic() {
        return true;
    }

    private String mainAttribute(Attributes.Name name) {
        if (manifest != null) {
            final Object value = manifest.getMainAttributes().get(name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private Manifest manifest() {
        try {
            Optional<Entry> manEntry = entries()
                .filter(e -> e.type().equals(CLASS_OR_RESOURCE))
                .filter(e -> e.name().endsWith("META-INF/MANIFEST.MF"))
                .findFirst();
            if (manEntry.isPresent()) {
                return new Manifest(manEntry.get().stream());
            } else {
                return null;
            }
        } catch (IOException e) {
            LOG.warn("Error reading manifest: %s", e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    private void collectDependencies() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final List<String> args = new ArrayList<>();

        args.add("--list-deps");
        if (isMultiRelease) {
            args.add("--multi-release");
            args.add(releaseFeatureVersion);
        }
        args.add(getPath().toString());

        final int result = JDEPS.run(new PrintStream(out), System.err, args.toArray(new String[0]));
        if (result != 0) {
            throw new RuntimeException("Could not collect dependencies of " + getPath());
        }

        final Set<String> jdkModules = javaModuleNames();
        Arrays.stream(out.toString().split("\\n"))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .filter(s -> !s.contains(" "))
              .forEach(name -> {
                  final int slash = name.indexOf('/');
                  if (slash > 0) {
                      name = name.substring(0, slash);
                  }
                  dependencies.add(name);
                  if (jdkModules.contains(name)) {
                      jdkDependencies.add(name);
                  }
              });
    }

    // TODO: Automatic modules just ignore illegal names (e.g. won't list "org.apache.commons.lang.enum" as a package) when
    //       creating the descriptor, BUT...
    //       The SystemModulesPlugin forces resolution using the packages COMPUTED from the ResourcePool, and also enforces
    //       legal package names. For now, just filter out all such entries; clearly some other solution is needed!
    private static Map<String, Set<String>> EXCLUDED_PACKAGES_BY_MODULE = Map.of(
        "commons.lang", Set.of("org/apache/commons/lang/enum/")
    );

    private void checkExcludedPackages(String moduleName) {
        final Set<String> excludedPackagePaths = EXCLUDED_PACKAGES_BY_MODULE.get(moduleName);
        if (excludedPackagePaths != null && !excludedPackagePaths.isEmpty()) {
            entryFilter(entry -> {
                for (String excludedPackage : excludedPackagePaths) {
                    if (entry.name().startsWith(excludedPackage)) {
                        LOG.warn("excluding illegal package '%s' from module '%s", entry.name(), moduleName);
                        return false;
                    }
                }
                return true;
            });
        }
    }
}
