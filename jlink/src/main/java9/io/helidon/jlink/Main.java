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

package io.helidon.jlink;

import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;

/**
 * TODO: Describe
 */
public class Main {

    // Ref: https://in.relation.to/2017/12/12/exploring-jlink-plugin-api-in-java-9/

    /*

        Invoking this tool the jlink wrapper (cleaner than using -J-*) and agent jar:

        java -javaagent:path/to/helidon-jlink.jar \
        --module-path path/to/modules \
        --module org.hibernate.demos.jlink/org.hibernate.demos.jlink.JLinkWrapper \
        --module-path $JAVA_HOME/jmods/:path/to/modules \
        --add-modules com.example.b \
        --output path/to/jlink-image \
        --add-index=com.example.b:for-modules=com.example.a

     */

    // TODO: create jandex index if needed

    public static void main(String... args){
        Optional<ToolProvider> jlink = ToolProvider.findFirst("jlink" );

        jlink.get().run(
            System.out,
            System.err,
            args
        );
    }

    /**
     * Agent entry point, required to add custom plugin. Assumes manifest "Launcher-Agent-Main" entry.
     * @param agentArgs
     * @param inst
     * @throws Exception
     */
    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        System.out.println("BEGIN premain");
        Module jlinkModule = ModuleLayer.boot().findModule( "jdk.jlink" ).get();
        Module addIndexModule = ModuleLayer.boot().findModule( "org.hibernate.demos.jlink" ).get();

        Map<String, Set<Module>> extraExports = new HashMap<>();
        extraExports.put("jdk.tools.jlink.plugin", Collections.singleton(addIndexModule ) );

        // alter jdk.jlink to export its API to the module with our indexing plug-in
        inst.redefineModule(
            jlinkModule, Collections.emptySet(), extraExports, Collections.emptyMap(),
            Collections.emptySet(), Collections.emptyMap()
        );

        Class<?> pluginClass = jlinkModule.getClassLoader()
                                          .loadClass( "jdk.tools.jlink.plugin.Plugin" );
        Class<?> addIndexPluginClass = addIndexModule.getClassLoader()
                                                     .loadClass( "org.hibernate.demos.jlink.plugins.AddIndexPlugin" );

        Map<Class<?>, List<Class<?>>> extraProvides = new HashMap<>();
        extraProvides.put( pluginClass, Collections.singletonList( addIndexPluginClass ) );

        // alter the module with the indexing plug-in so it provides the plug-in as a service
        inst.redefineModule(
            addIndexModule, Collections.emptySet(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptySet(), extraProvides
        );
        System.out.println("END premain");
    }
}
