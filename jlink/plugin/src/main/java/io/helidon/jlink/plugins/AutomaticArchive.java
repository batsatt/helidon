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
import java.nio.file.Path;
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
    private final boolean isMultiRelease;
    private final String releaseFeatureVersion;

    /**
     * Constructor.
     *
     * @param delegate The delegate archive.
     * @param descriptor The descriptor.
     * @param version The archive version.
     * @param allJdkModules The names of all JDK modules.
     * @param jdkVersion The JDK version.
     */
    AutomaticArchive(Archive delegate,
                     ModuleDescriptor descriptor,
                     Runtime.Version version,
                     Set<String> allJdkModules,
                     Runtime.Version jdkVersion) {
        this(delegate, descriptor, allJdkModules, version, jdkVersion, manifest(delegate));
    }

    private AutomaticArchive(Archive delegate,
                             ModuleDescriptor descriptor,
                             Set<String> javaModuleNames,
                             Runtime.Version version,
                             Runtime.Version jdkVersion,
                             Manifest manifest) {
        this(delegate, descriptor, javaModuleNames, version, isMultiRelease(manifest),
             Integer.toString(jdkVersion.feature()));
    }

    private AutomaticArchive(Archive delegate,
                             ModuleDescriptor descriptor,
                             Set<String> javaModuleNames,
                             Runtime.Version version,
                             boolean isMultiRelease,
                             String releaseFeatureVersion) {
        super(delegate, descriptor, version, javaModuleNames);
        this.isMultiRelease = isMultiRelease;
        this.releaseFeatureVersion = releaseFeatureVersion;
    }

    @Override
    public boolean isAutomatic() {
        return true;
    }

    @Override
    protected Set<String> collectDependencies(Map<String, DelegatingArchive> appArchivesByExport) {
        final Path modulePath = delegate().getPath();
        final String jarName = modulePath.getFileName().toString();

        final List<String> args = new ArrayList<>();

        if (isMultiRelease) {
            args.add("--multi-release");
            args.add(releaseFeatureVersion);
        }

        args.add(modulePath.toString());

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int result = JDEPS.run(new PrintStream(out), System.err, args.toArray(new String[0]));
        if (result != 0) {
            throw new RuntimeException("Could not collect dependencies of " + modulePath);
        }

        // Parse the jdeps output to collect known dependencies and, where not found, map those
        // packages to the exporting module, if possible

        final Set<String> dependencies = new HashSet<>();

        Arrays.stream(out.toString().split("\\n")) // TODO EOL
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .filter(s -> !s.contains(jarName)) // references to self
              .forEach(line -> {
                  final int arrow = line.indexOf("->");
                  if (arrow >= 0) {
                      final String mapping = line.substring(arrow + 2).trim();
                      final int firstSpace = mapping.indexOf(' ');
                      if (firstSpace >= 0) {
                          final String pkgName = mapping.substring(0, firstSpace).trim();
                          final String provider = mapping.substring(firstSpace).trim();
                          if (!SpecialCases.isDynamicPackage(pkgName)) {
                              if (provider.contains(" ")) {
                                  if (provider.equals("not found")) {
                                      final DelegatingArchive exporter = appArchivesByExport.get(pkgName);
                                      if (exporter == null) {
                                          LOG.warn("Could not find exporter for required package %s", pkgName);
                                      } else {
                                          dependencies.add(exporter.moduleName());
                                      }
                                  } else if (provider.toLowerCase().contains("internal")) {
                                      final int open = provider.indexOf('(');
                                      final int close = provider.indexOf(')');
                                      if (open > 0 && close > 0) {
                                          final String internalProvider = provider.substring(open + 1, close);
                                          if (internalProvider.contains(" ")) {
                                              LOG.debug("Ignoring internal %s", internalProvider);
                                          } else {
                                              LOG.debug("Adding internal provider %s", internalProvider);
                                              dependencies.add(internalProvider);
                                          }
                                      } else {
                                          LOG.debug("Unknown provider format for package %s: %s", pkgName, provider);
                                      }
                                  } else {
                                      LOG.debug("Unknown provider format for package %s: %s", pkgName, provider);
                                  }
                              } else {
                                  dependencies.add(provider);
                              }
                          }
                      } else {
                          LOG.debug("Unknown result format: %s", mapping);
                      }
                  } else {
                      LOG.debug("Unknown result format: %s", line);
                  }
              });

        return dependencies;
    }

    @Override
    protected ModuleDescriptor updateDescriptor(ModuleDescriptor descriptor) {
        if (isMultiRelease) {
            LOG.info("   Multi release version: %s", releaseFeatureVersion);
        }

        // Setup excluded packages filter if needed

        checkExcludedPackages(descriptor().name());

        // Update the descriptor to require all discovered dependencies

        return updateAutomaticDescriptor(descriptor, dependencies(), version());
    }

    private static boolean isMultiRelease(Manifest manifest) {
        return "true".equalsIgnoreCase(mainAttribute(manifest, Attributes.Name.MULTI_RELEASE));
    }

    private static String mainAttribute(Manifest manifest, Attributes.Name name) {
        if (manifest != null) {
            final Object value = manifest.getMainAttributes().get(name);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private static Manifest manifest(Archive delegate) {
        try {
            Optional<Entry> manEntry = delegate.entries()
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

    private void checkExcludedPackages(String moduleName) {
        final Set<String> excludedPackagePaths = SpecialCases.excludedPackagePaths(moduleName);
        if (!excludedPackagePaths.isEmpty()) {
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
