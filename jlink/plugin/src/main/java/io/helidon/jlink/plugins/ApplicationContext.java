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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Application context that is shared across components.
 */
public abstract class ApplicationContext {
    private static final AtomicReference<ApplicationContext> INSTANCE = new AtomicReference<>();

    /**
     * Set the context.
     *
     * @param context The context.
     * @return The context.
     */
    static ApplicationContext set(ApplicationContext context) {
        INSTANCE.set(requireNonNull(context));
        return context;
    }

    /**
     * Returns the content.
     *
     * @return The context.
     */
    public static ApplicationContext get() {
        return requireNonNull(INSTANCE.get());
    }

    /**
     * Tests whether or not we are targeting a Java Home other than the one we are running in.
     *
     * @return {@code true} if using an alternate.
     */
    public boolean isAlternateJavaHome() {
        return !javaHome().equals(Environment.JAVA_HOME);
    }

    /**
     * Returns the Java Home path.
     *
     * @return The path.
     */
    public abstract Path javaHome();

    /**
     * Returns whether or not the application uses Microprofile.
     *
     * @return {@code true} if using Microprofile.
     */
    public abstract boolean isMicroprofile();

    /**
     * Returns whether or not the application uses Weld. This should always return {@code true} if using Microprofile unless
     * the Helidon CDI implementation changes.
     *
     * @return {@code true} if using Weld.
     */
    public abstract boolean usesWeld();

    /**
     * Returns a map of all archives indexed by the packages they export.
     *
     * @return The index.
     */
    abstract Map<String, DelegatingArchive> archivesByPackage();

    /**
     * Returns the name of the application module.
     *
     * @return The name.
     */
    public abstract String applicationModuleName();

    /**
     * Returns the path to the class list file.
     *
     * @return The path.
     */
    public abstract Path classListFile();


    /**
     * Returns the class list.
     *
     * @return The list.
     */
    public abstract List<String> classList();
}
