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
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jdk.internal.module.ModuleTarget;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.ModuleTargetAttribute;

import static java.util.Collections.synchronizedSet;
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
    private static final Set<String> AUTOMATIC_MODULES = synchronizedSet(new HashSet<>());

    /**
     * Transforms a descriptor to an open descriptor that exports all packages and requires the
     * given modules. The module name is registered as one that should be treated as automatic.
     *
     * @param descriptor The descriptor.
     * @param extraRequires Extra required modules.
     * @return The original or updated descriptor.
     */
    static ModuleDescriptor updateAutomaticDescriptor(ModuleDescriptor descriptor, Set<String> extraRequires) {
        if (descriptor.isAutomatic()) {
            final ModuleDescriptor result = addExportsAndRequires(descriptor, extraRequires);
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
     * Exports all packages and add extra requires. Since automatic modules cannot export packages,
     * convert it to an open module.
     *
     * @param descriptor The descriptor.
     * @param extraRequires The extra required modules.
     * @return The updated descriptor.
     */
    private static ModuleDescriptor addExportsAndRequires(ModuleDescriptor descriptor, Set<String> extraRequires) {

        // Create a new open module descriptor from the existing one and export all packages.

        final ModuleDescriptor.Builder builder = ModuleDescriptor.newOpenModule(descriptor.name());
        if (descriptor.version().isPresent()) {
            builder.version(descriptor.version().get());
        }

        final Set<String> packages = descriptor.packages();
        builder.packages(packages);
        packages.forEach(builder::exports);

        descriptor.provides().forEach(builder::provides);
        extraRequires.forEach(moduleName -> {
            if (!moduleName.equals("java.base")) {
                builder.requires(moduleName);
            }
        });

        if (descriptor.mainClass().isPresent()) {
            builder.mainClass(descriptor.mainClass().get());
        }

        return builder.build();
    }


    /**
     * Copy from jdk.internal that maps automatic module modifiers to no flat.
     */
    private static class ModuleInfoWriter {

        private static final Map<ModuleDescriptor.Modifier, Integer>
            MODULE_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Modifier.OPEN, ACC_OPEN,
            ModuleDescriptor.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            ModuleDescriptor.Modifier.MANDATED, ACC_MANDATED,
            ModuleDescriptor.Modifier.AUTOMATIC, 0 // The only change!
        );

        private static final Map<ModuleDescriptor.Requires.Modifier, Integer>
            REQUIRES_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Requires.Modifier.TRANSITIVE, ACC_TRANSITIVE,
            ModuleDescriptor.Requires.Modifier.STATIC, ACC_STATIC_PHASE,
            ModuleDescriptor.Requires.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.MANDATED, ACC_MANDATED
        );

        private static final Map<ModuleDescriptor.Exports.Modifier, Integer>
            EXPORTS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Exports.Modifier.SYNTHETIC, ACC_SYNTHETIC,
            ModuleDescriptor.Exports.Modifier.MANDATED, ACC_MANDATED
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
            for (ModuleDescriptor.Exports e : md.exports()) {
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
                                        .map(ModuleDescriptor.Exports::source);
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
