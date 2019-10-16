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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Modifier;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.jlink.logging.Log;

import jdk.internal.module.ModuleTarget;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.ModuleTargetAttribute;

import static java.util.Collections.emptyMap;
import static java.util.Collections.synchronizedSet;
import static java.util.stream.Collectors.toMap;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_MANDATED;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_MODULE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_OPEN;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC_PHASE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_TRANSITIVE;

/**
 * Module descriptor utilities.
 */
class ModuleDescriptors {
    private static final Log LOG = Log.getLog("module-descriptors");
    private static final Set<String> AUTOMATIC_MODULES = synchronizedSet(new HashSet<>());

    /**
     * Transforms a descriptor to an open descriptor that exports all packages and requires the
     * given modules. The module name is registered as one that should be treated as automatic.
     *
     * @param descriptor The descriptor.
     * @param additionalRequires Extra required modules.
     * @param version The version. May be {@code null}.
     * @return The original or updated descriptor.
     */
    static ModuleDescriptor updateAutomaticDescriptor(ModuleDescriptor descriptor,
                                                      Set<String> additionalRequires,
                                                      Runtime.Version version) {
        if (descriptor.isAutomatic()) {
            final ModuleDescriptor result = convertToOpen(descriptor, version, additionalRequires, true);
            AUTOMATIC_MODULES.add(descriptor.name());
            return result;
        } else {
            throw new IllegalArgumentException(descriptor.name() + " is not automatic");
        }
    }

    /**
     * Returns whether or not the given module has been registered as automatic.
     *
     * @param moduleName The module name.
     * @return {@code true} if registered as automatic.
     */
    static boolean isAutomatic(String moduleName) {
        return AUTOMATIC_MODULES.contains(moduleName);
    }

    /**
     * Returns the automatic module names.
     *
     * @return The names.
     */
    static Set<String> automaticModules() {
        return AUTOMATIC_MODULES;
    }

    /**
     * Compile a descriptor to a module-info.class byte array.
     *
     * @param descriptor The descriptor.
     * @return The compiled descriptor.
     */
    static byte[] compile(ModuleDescriptor descriptor) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ModuleInfoWriter.write(descriptor, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Convert the given descriptor to an open one.
     *
     * @param descriptor The descriptor.
     * @param additionalRequires
     * @return The updated descriptor.
     */
    static ModuleDescriptor convertToOpen(ModuleDescriptor descriptor, Set<String> additionalRequires) {
        return update(descriptor, Set.of(Modifier.OPEN), null, emptyMap(), additionalRequires, false);
    }

    /**
     * Convert the given descriptor to an open one. Optionally add a version and/or requires and export all packages.
     *
     * @param descriptor The descriptor.
     * @param version The version. If the given descriptor does not have a version, this one is used if not {@code null}.
     * @param additionalRequires Any additional required modules. May be empty.
     * @param exportPackages {@code true} if all packages should be exported.
     * @return The updated descriptor.
     */
    static ModuleDescriptor convertToOpen(ModuleDescriptor descriptor,
                                          Runtime.Version version,
                                          Set<String> additionalRequires,
                                          boolean exportPackages) {

        return update(descriptor, Set.of(Modifier.OPEN), version, emptyMap(), additionalRequires, exportPackages);
    }

    /**
     * Update requires in the given descriptor to perform substitutions and additions.
     *
     * @param descriptor The descriptor.
     * @param substituteRequires The substitutions.
     * @param additionalRequires The additions.
     * @return The updated descriptor.
     */
    static ModuleDescriptor updateRequires(ModuleDescriptor descriptor,
                                           Map<String, String> substituteRequires,
                                           Set<String> additionalRequires) {

        if (needsUpdate(descriptor, substituteRequires) || needsUpdate(descriptor, additionalRequires)) {
            return update(descriptor, null, null, substituteRequires, additionalRequires, false);
        } else {
            return descriptor;
        }
    }


    /**
     * Copy and update a descriptor.
     *
     * @param descriptor The descriptor.
     * @param modifiers The modifiers. The original modifiers are used if {@code null}.
     * @param version The version. If the given descriptor does not have a version, this one is used if not {@code null}.
     * @param substituteRequires Any substitute requires. May be empty.
     * @param additionalRequires Any additional required modules. May be empty.
     * @param exportAllPackages {@code true} if all packages should be exported; any existing exports will be copied.
     * @return The updated descriptor.
     */
    private static ModuleDescriptor update(ModuleDescriptor descriptor,
                                           Set<Modifier> modifiers,
                                           Runtime.Version version,
                                           Map<String, String> substituteRequires,
                                           Set<String> additionalRequires,
                                           boolean exportAllPackages) {

        final String moduleName = descriptor.name();
        final Set<Modifier> newModifiers = modifiers == null ? descriptor.modifiers() : modifiers;
        final boolean isOpen = newModifiers.contains(Modifier.OPEN);
        final ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(moduleName, newModifiers);

        if (descriptor.version().isPresent()) {
            builder.version(descriptor.version().get());
        } else if (version != null) {
            builder.version(version.toString());
        }

        if (descriptor.mainClass().isPresent()) {
            builder.mainClass(descriptor.mainClass().get());
        }

        descriptor.provides().forEach(builder::provides);

        descriptor.uses().forEach(builder::uses);

        builder.packages(descriptor.packages());

        // Drop any explicit opens if the new descriptor is open, otherwise just copy them

        if (!isOpen) {
            descriptor.opens().forEach(builder::opens);
        }

        // Export all packages if requested, otherwise just copy them

        if (exportAllPackages) {
            final Set<String> allPackages = descriptor.packages();
            final Map<String, Exports> existing = descriptor.exports()
                                                            .stream()
                                                            .collect(toMap(Exports::source, e -> e));
            allPackages.forEach(pkgName -> {
                final Exports exports = existing.get(pkgName);
                if (exports == null) {
                    builder.exports(pkgName);
                } else {
                    builder.exports(exports);
                }
            });
        } else {
            descriptor.exports().forEach(builder::exports);
        }

        // Copy requires, performing substitutions if needed

        descriptor.requires().forEach(r -> {
            final String name = r.name();
            final String substitute = substituteRequires.get(name);
            if (substitute == null) {
                builder.requires(r);
            } else if (substitute.equals(moduleName)) {
                // Drop it.
                LOG.info("Dropping requires " + name + " from " + moduleName);
            } else {
                LOG.info("Substituting %s for %s in %s", substitute, name, moduleName);
                if (r.compiledVersion().isPresent()) {
                    builder.requires(r.modifiers(), substitute, r.compiledVersion().get());
                } else {
                    builder.requires(r.modifiers(), substitute);
                }
            }
        });

        // Add requires if needed

        if (!additionalRequires.isEmpty()) {
            final Set<String> allRequires = descriptor.requires()
                                                      .stream()
                                                      .map(ModuleDescriptor.Requires::name)
                                                      .collect(Collectors.toSet());
            additionalRequires.forEach(extra -> {
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
        }

        return builder.build();
    }

    private static boolean needsUpdate(ModuleDescriptor descriptor, Map<String, String> substituteRequires) {
        for (ModuleDescriptor.Requires require : descriptor.requires()) {
            if (substituteRequires.containsKey(require.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsUpdate(ModuleDescriptor descriptor, Set<String> extraRequires) {
        for (ModuleDescriptor.Requires require : descriptor.requires()) {
            if (!extraRequires.contains(require.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copy from jdk.internal that maps automatic module modifier to an unset flag.
     */
    private static class ModuleInfoWriter {

        private static final Map<Modifier, Integer>
            MODULE_MODS_TO_FLAGS = Map.of(
            Modifier.OPEN, ACC_OPEN,
            Modifier.SYNTHETIC, ACC_SYNTHETIC,
            Modifier.MANDATED, ACC_MANDATED,
            Modifier.AUTOMATIC, 0 // The only change!
        );

        private static final Map<ModuleDescriptor.Requires.Modifier, Integer>
            REQUIRES_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Requires.Modifier.TRANSITIVE, ACC_TRANSITIVE,
            ModuleDescriptor.Requires.Modifier.STATIC, ACC_STATIC_PHASE,
            ModuleDescriptor.Requires.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.MANDATED, ACC_MANDATED
        );

        private static final Map<Exports.Modifier, Integer>
            EXPORTS_MODS_TO_FLAGS = Map.of(
            Exports.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            Exports.Modifier.MANDATED, ACC_MANDATED
        );

        private static final Map<ModuleDescriptor.Opens.Modifier, Integer>
            OPENS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Opens.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            ModuleDescriptor.Opens.Modifier.MANDATED, ACC_MANDATED
        );

        private static final String[] EMPTY_STRING_ARRAY = new String[0];

        private ModuleInfoWriter() { }

        /**
         * Writes the given module descriptor to a module-info.class file,
         * returning it in a byte array.
         */
        private static byte[] toModuleInfo(ModuleDescriptor md, ModuleTarget target) {
            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V10, ACC_MODULE, "module-info", null, null, null);

            int moduleFlags = md.modifiers().stream()
                                .map(MODULE_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
            String vs = md.rawVersion().orElse(null);
            ModuleVisitor mv = cw.visitModule(md.name(), moduleFlags, vs);

            // requires
            for (ModuleDescriptor.Requires r : md.requires()) {
                int flags = r.modifiers().stream()
                             .map(REQUIRES_MODS_TO_FLAGS::get)
                             .reduce(0, (x, y) -> (x | y));
                vs = r.rawCompiledVersion().orElse(null);
                mv.visitRequire(r.name(), flags, vs);
            }

            // exports
            for (Exports e : md.exports()) {
                int flags = e.modifiers().stream()
                             .map(EXPORTS_MODS_TO_FLAGS::get)
                             .reduce(0, (x, y) -> (x | y));
                String[] targets = e.targets().toArray(EMPTY_STRING_ARRAY);
                mv.visitExport(e.source().replace('.', '/'), flags, targets);
            }

            // opens
            for (ModuleDescriptor.Opens opens : md.opens()) {
                int flags = opens.modifiers().stream()
                                 .map(OPENS_MODS_TO_FLAGS::get)
                                 .reduce(0, (x, y) -> (x | y));
                String[] targets = opens.targets().toArray(EMPTY_STRING_ARRAY);
                mv.visitOpen(opens.source().replace('.', '/'), flags, targets);
            }

            // uses
            md.uses().stream().map(sn -> sn.replace('.', '/')).forEach(mv::visitUse);

            // provides
            for (ModuleDescriptor.Provides p : md.provides()) {
                mv.visitProvide(p.service().replace('.', '/'),
                                p.providers()
                                 .stream()
                                 .map(pn -> pn.replace('.', '/'))
                                 .toArray(String[]::new));
            }

            // add the ModulePackages attribute when there are packages that aren't
            // exported or open
            Stream<String> exported = md.exports().stream()
                                        .map(Exports::source);
            Stream<String> open = md.opens().stream()
                                    .map(ModuleDescriptor.Opens::source);
            long exportedOrOpen = Stream.concat(exported, open).distinct().count();
            if (md.packages().size() > exportedOrOpen) {
                md.packages().stream()
                  .map(pn -> pn.replace('.', '/'))
                  .forEach(mv::visitPackage);
            }

            // ModuleMainClass attribute
            md.mainClass()
              .map(mc -> mc.replace('.', '/'))
              .ifPresent(mv::visitMainClass);

            mv.visitEnd();

            // write ModuleTarget attribute if there is a target platform
            if (target != null && target.targetPlatform().length() > 0) {
                cw.visitAttribute(new ModuleTargetAttribute(target.targetPlatform()));
            }

            cw.visitEnd();
            return cw.toByteArray();
        }

        /**
         * Writes a module descriptor to the given output stream as a
         * module-info.class.
         */
        public static void write(ModuleDescriptor descriptor,
                                 ModuleTarget target,
                                 OutputStream out) throws IOException {
            byte[] bytes = toModuleInfo(descriptor, target);
            out.write(bytes);
        }

        /**
         * Writes a module descriptor to the given output stream as a
         * module-info.class.
         */
        public static void write(ModuleDescriptor descriptor, OutputStream out) throws IOException {
            write(descriptor, null, out);
        }

        /**
         * Returns a {@code ByteBuffer} containing the given module descriptor
         * in module-info.class format.
         */
        public static ByteBuffer toByteBuffer(ModuleDescriptor descriptor) {
            byte[] bytes = toModuleInfo(descriptor, null);
            return ByteBuffer.wrap(bytes);
        }
    }
}
