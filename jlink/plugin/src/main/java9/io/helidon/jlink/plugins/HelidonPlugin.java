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

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

/**
 * TODO: Describe
 */
public class HelidonPlugin implements Plugin {
    private static final String NAME = "helidon";

    private Path appModulePath;
    private Path appLibsDir;

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
        System.out.println("appModulePath: " + appModulePath);
        System.out.println("   appLibsDir: " + appModulePath);
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        return null;
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

}
