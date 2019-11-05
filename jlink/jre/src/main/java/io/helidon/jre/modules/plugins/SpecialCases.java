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

package io.helidon.jre.modules.plugins;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.IndexView;

/**
 * Utilities for all special cases.
 */
public class SpecialCases {
    private static final Set<String> DYNAMIC_PACKAGES = Set.of("org.slf4j.impl");

    // TODO: Automatic modules just ignore illegal names (e.g. won't list "org.apache.commons.lang.enum" as a package) when
    //       creating the descriptor, BUT...
    //       The SystemModulesPlugin forces resolution using the packages COMPUTED from the ResourcePool, and also enforces
    //       legal package names. For now, just filter out all such entries; clearly some other solution is needed!
    private static Map<String, Set<String>> EXCLUDED_PACKAGES_BY_MODULE = Map.of(
        "commons.lang", Set.of("org/apache/commons/lang/enum/")
    );

    private static final Map<String, String> FILE_NAME_TO_MODULE_NAME = Map.of(
        "jboss-interceptors-api_1.2_spec-1.0.0.Final.jar", // Doesn't have an "Automatic-Module-Name" manifest entry
        "javax.interceptor.api:1.2"
    );

    private static final String MICROPROFILE_MODULE_NAME_PREFIX = "microprofile.";
    private static final Set<String> INJECT_MODULE_NAMES = Set.of("jakarta.inject", "javax.inject");
    private static Set<String> ADDITIONAL_WELD_REQUIRES = Set.of("weld.api", "weld.core.impl");

    /**
     * Tests whether or not the given package should be considered dynamic (e.g. should not have a require directive).
     *
     * @param packageName The package name.
     * @return {@code true} if the package is dynamic.
     */
    public static boolean isDynamicPackage(String packageName) {
        return DYNAMIC_PACKAGES.contains(packageName);
    }

    /**
     * Returns the module name for the given jar file name, if known.
     *
     * @param jarFileName The jar file name.
     * @return The module name or {@code null} if not known.
     */
    public static String moduleNameFor(String jarFileName) {
        return FILE_NAME_TO_MODULE_NAME.get(jarFileName);
    }

    /**
     * Returns the (likely empty) set of packages that should be excluded for the given module, in path form.
     *
     * @param moduleName The module.
     * @return The set.
     */
    public static Set<String> excludedPackagePaths(String moduleName) {
        final Set<String> result = EXCLUDED_PACKAGES_BY_MODULE.get(moduleName);
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * Returns the (likely empty) set of additional requires for the given archive if it relies on Weld
     * and this is an MP app.
     *
     * @param context The context.
     * @param archive The archive.
     * @param index The index if archive contains CDI beans or {@code null} if not.
     * @return The additional requires.
     */
    public static Set<String> additionalWeldRequires(ApplicationContext context,
                                                     DelegatingArchive archive,
                                                     IndexView index) {
        if (context.isMicroprofile()) {
            if (index != null && context.usesWeld() && archive.isAutomatic()) {
                // TODO: see if we can refine this coarse grained approach by examining the index
                return ADDITIONAL_WELD_REQUIRES;
            } else if (archive.moduleName().startsWith(MICROPROFILE_MODULE_NAME_PREFIX)
                       && archive.dependencies().stream().anyMatch(INJECT_MODULE_NAMES::contains)) {
                // This is an MP module that uses injection, so add weld
                return ADDITIONAL_WELD_REQUIRES;
            }
        }
        return Collections.emptySet();
    }
}
