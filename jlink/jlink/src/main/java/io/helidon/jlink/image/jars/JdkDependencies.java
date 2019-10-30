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

package io.helidon.jlink.image.jars;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import io.helidon.jlink.common.logging.Log;

import static java.util.Objects.requireNonNull;

/**
 * Utility to collect JDK dependencies for a collection of jars.
 */
public class JdkDependencies {
    private static final Log LOG = Log.getLog("jdk-dependencies");
    private static final String JDEPS_TOOL_NAME = "jdeps";
    private static final String MULTI_RELEASE_ARG = "--multi-release";
    private static final String SYSTEM_ARG = "--system";
    private static final String LIST_DEPS_ARG = "--list-deps";
    private static final String JAVA_BASE_MODULE_NAME = "java.base";
    private static final String EOL = System.getProperty("line.separator");
    private static final ToolProvider JDEPS = ToolProvider.findFirst(JDEPS_TOOL_NAME).orElseThrow();
    private final JavaHome javaHome;
    private final Set<String> javaModuleNames;
    private final Set<String> dependencies;

    public JdkDependencies(JavaHome javaHome) {
        this.javaHome = requireNonNull(javaHome);
        this.javaModuleNames = javaHome.moduleNames();
        this.dependencies = new HashSet<>();
        this.dependencies.add(JAVA_BASE_MODULE_NAME);
    }

    public Set<String> collect(Stream<Jar> jars) {
        jars.forEach(jar -> {
            if (jar.hasModuleDescriptor()) {
                addModule(jar);
            } else {
                addJar(jar);
            }
        });
        return dependencies;
    }

    private void addModule(Jar module) {
        module.moduleDescriptor()
              .requires()
              .stream()
              .map(ModuleDescriptor.Requires::name)
              .filter(javaModuleNames::contains)
              .forEach(dependencies::add);
    }

    private void addJar(Jar jar) {
        LOG.info("Collecting dependencies of %s", jar.path());
        final List<String> args = new ArrayList<>();
        if (!javaHome.isCurrent()) {
            args.add(SYSTEM_ARG);
            args.add(javaHome.path().toString());
        }
        if (jar.isMultiRelease()) {
            args.add(MULTI_RELEASE_ARG);
            args.add(javaHome.featureVersion());
        }
        args.add(LIST_DEPS_ARG);
        args.add(jar.path().toString());

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int result = JDEPS.run(new PrintStream(out), System.err, args.toArray(new String[0]));
        if (result != 0) {
            throw new RuntimeException("Could not collect dependencies of " + jar.path());
        }

        Arrays.stream(out.toString().split(EOL))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .filter(javaModuleNames::contains)
              .forEach(dependencies::add);
    }
}
