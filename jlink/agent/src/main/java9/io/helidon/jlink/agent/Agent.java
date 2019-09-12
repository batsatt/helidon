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

package io.helidon.jlink.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * An instrumentation agent that provides a work-around for the fact that jlink does
 * not yet export the packages required to implement a plugin.
 * See https://in.relation.to/2017/12/12/exploring-jlink-plugin-api-in-java-9/ for
 * more details.
 */
public class Agent {

    /**
     * Agent entry point. Assumes manifest "Premain-Class" entry.
     *
     * @param agentArgs Args passed explicitly to agent.
     * @param inst Instrumentation instance.
     * @throws Exception If an error occurs.
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        System.out.println("BEGIN premain");

        // Get jdk and helidon jlink modules

        Module jlinkModule = findModule("jdk.jlink");
        Module helidonModule = findModule("helidon.jlink");

        // Modify the jdk module to export its plugin and internal packages to our module

        Set<Module> exportTo = singleton(helidonModule);
        Map<String, Set<Module>> extraExports = Map.of("jdk.tools.jlink.plugin", exportTo,
                                                       "jdk.tools.jlink.internal", exportTo);
        inst.redefineModule(jlinkModule, emptySet(), extraExports, emptyMap(), emptySet(), emptyMap());

        // Modify our module so it provides our plug-in as a service

        Class<?> pluginClass = loadClass(jlinkModule, "jdk.tools.jlink.plugin.Plugin");
        Class<?> helidonPluginClass = loadClass(helidonModule, "io.helidon.jlink.plugins.HelidonPlugin");
        Map<Class<?>, List<Class<?>>> extraProvides = Map.of(pluginClass, singletonList(helidonPluginClass));
        inst.redefineModule(helidonModule, emptySet(), emptyMap(), emptyMap(), emptySet(), extraProvides);

        System.out.println("END premain");
    }

    private static Module findModule(String moduleName) {
        return ModuleLayer.boot().findModule(moduleName).orElseThrow();
    }

    private static Class<?> loadClass(Module module, String className) throws Exception {
        return module.getClassLoader().loadClass(className);
    }
}
