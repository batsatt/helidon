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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
     * Returns the (likely empty) set of packages that should be excluded for the given module, in path form.
     *
     * @param moduleName The module.
     * @return The set.
     */
    public static Set<String> excludedPackagePaths(String moduleName) {
        final Set<String> result = EXCLUDED_PACKAGES_BY_MODULE.get(moduleName);
        return result == null ? Collections.emptySet() : result;
    }
}
