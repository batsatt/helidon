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

        // Modify the jdk.jlink module to export its plugin and internal packages to our module

        Module jlinkModule = findModule("jdk.jlink");
        Module helidonModule = findModule("helidon.jlink");
        Set<Module> exportTo = singleton(helidonModule);
        Map<String, Set<Module>> extraExports = Map.of("jdk.tools.jlink.plugin", exportTo,
                                                       "jdk.tools.jlink.internal", exportTo,
                                                       "jdk.tools.jlink.internal.plugins", exportTo);
        inst.redefineModule(jlinkModule, emptySet(), extraExports, emptyMap(), emptySet(), emptyMap());

        // Modify the java.base module to export its jdk.internal.module and asm packages to our module

        Module javaBaseModule = findModule("java.base");
        extraExports = Map.of("jdk.internal.module", exportTo,
                              "jdk.internal.org.objectweb.asm", exportTo,
                              "jdk.internal.org.objectweb.asm.commons", exportTo);
        inst.redefineModule(javaBaseModule, emptySet(), extraExports, emptyMap(), emptySet(), emptyMap());

        // Modify our module so it provides our plug-ins as services

        Class<?> pluginClass = loadClass(jlinkModule, "jdk.tools.jlink.plugin.Plugin");
        Class<?> helidonPluginClass = loadClass(helidonModule, "io.helidon.jlink.plugins.HelidonPlugin");
        Class<?> bootModulesPluginClass = loadClass(helidonModule, "io.helidon.jlink.plugins.BootModulesPlugin");
        Class<?> bootOrderPluginClass = loadClass(helidonModule, "io.helidon.jlink.plugins.BootOrderPlugin");
        Map<Class<?>, List<Class<?>>> extraProvides = Map.of(pluginClass, List.of(
            helidonPluginClass, bootModulesPluginClass, bootOrderPluginClass));
        inst.redefineModule(helidonModule, emptySet(), emptyMap(), emptyMap(), emptySet(), extraProvides);
    }

    private static Module findModule(String moduleName) {
        return ModuleLayer.boot().findModule(moduleName).orElseThrow();
    }

    private static Class<?> loadClass(Module module, String className) throws Exception {
        return module.getClassLoader().loadClass(className);
    }
}
