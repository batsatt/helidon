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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO: Describe
 */
public class Agent {

    // Ref: https://in.relation.to/2017/12/12/exploring-jlink-plugin-api-in-java-9/

    /*

        Invoking this tool via the jlink wrapper (cleaner than using -J-*) and agent jar:

        java -javaagent:path/to/helidon-jlink-agent.jar \
        --module-path path/to/modules \
        --module helidon.jlink \
        --module-path $JAVA_HOME/jmods/:path/to/modules \
        --add-modules com.example.b \
        --output path/to/jlink-image \
        --helidon=com.example.b:for-modules=com.example.a

     */

    /**
     * Agent entry point, required to add custom plugin. Assumes manifest "Premain-Class" entry.
     *
     * @param agentArgs Args passed explicitly to agent.
     * @param inst Instrumentation instance.
     * @throws Exception If an error occurs.
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        System.out.println("BEGIN premain");
        Module jlinkModule = ModuleLayer.boot().findModule("jdk.jlink").orElseThrow();
        Module helidonModule = ModuleLayer.boot().findModule("helidon.jlink").orElseThrow();

        Map<String, Set<Module>> extraExports = new HashMap<>();
        extraExports.put("jdk.tools.jlink.plugin", Collections.singleton(helidonModule));

        // Modify jdk.jlink to export its API to the module with our plug-in

        inst.redefineModule(
            jlinkModule, Collections.emptySet(), extraExports, Collections.emptyMap(),
            Collections.emptySet(), Collections.emptyMap()
        );

        Class<?> pluginClass = jlinkModule.getClassLoader()
                                          .loadClass("jdk.tools.jlink.plugin.Plugin");
        Class<?> helidonPluginClass = helidonModule.getClassLoader()
                                                   .loadClass("io.helidon.jlink.plugins.HelidonPlugin");

        Map<Class<?>, List<Class<?>>> extraProvides = new HashMap<>();
        extraProvides.put(pluginClass, Collections.singletonList(helidonPluginClass));

        // Modify our module so it provides the plug-in as a service

        inst.redefineModule(
            helidonModule, Collections.emptySet(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptySet(), extraProvides
        );
        System.out.println("END premain");
    }
}
