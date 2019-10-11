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

import java.nio.file.Path;
import java.util.Map;

/**
 * TODO: Describe
 */
public interface ApplicationContext {
    default boolean isAlternateJavaHome() {
        return !javaHome().equals(Environment.JAVA_HOME);
    }

    Path javaHome();

    boolean isMicroprofile();

    boolean usesWeld();

    Map<String, DelegatingArchive> archivesByPackage();
}
