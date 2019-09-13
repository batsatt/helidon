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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.Archive;

import static jdk.tools.jlink.internal.Archive.Entry.EntryType.CLASS_OR_RESOURCE;

/**
 * An archive representing an automatic module.
 */
public class AutomaticArchive implements Archive {
    private static final ToolProvider JDEPS = ToolProvider.findFirst("jdeps").orElseThrow();
    private final Archive delegate;
    private final Runtime.Version version;
    private final Manifest manifest;
    private final boolean isMultiRelease;
    private final String releaseFeatureVersion;
    private final List<String> jdkModuleDependencies;

    /**
     * Constructor.
     *
     * @param delegate The delegate archive.
     * @param version The archive version.
     */
    public AutomaticArchive(Archive delegate, Runtime.Version version) {
        this.delegate = delegate;
        this.version = version;
        this.manifest = manifest();
        this.isMultiRelease = "true".equalsIgnoreCase(mainAttribute(Attributes.Name.MULTI_RELEASE));
        this.releaseFeatureVersion = Integer.toString(version.feature());
        this.jdkModuleDependencies = jdkModuleDependencies();
        System.out.println(delegate.moduleName() + " -> " + jdkModuleDependencies);
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
            throw new UncheckedIOException(e);
        }
    }

    private List<String> jdkModuleDependencies() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final List<String> args = new ArrayList<>();

        args.add("--list-deps");
        if (isMultiRelease) {
            args.add("--multi-release");
            args.add(releaseFeatureVersion);
        }
        args.add(delegate.getPath().toString());

        final int result = JDEPS.run(new PrintStream(out), System.err, args.toArray(new String[0]));
        if (result != 0) {
            throw new RuntimeException("Could not collect dependencies of " + delegate.getPath());
        }

        return Arrays.stream(out.toString().split("\\n"))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toList());
    }
}
