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

package io.helidon.common.logging;

import java.lang.reflect.Method;

import io.helidon.common.Builder;
import io.helidon.config.Config;

/**
 * A {@link Builder} that accepts a root {@link Config} instance.
 *
 * @since 2019-02-03
 */
public interface ConfigurableBuilder<T> extends Builder<T> {   // TODO move, maybe to common-configurable

    /**
     * Update this builder with relevant properties from the given config instance.
     *
     * @param config The config instance.
     * @return This instance, for chaining.
     */
    ConfigurableBuilder<T> config(final Config config);

    /**
     * Returns a new configurable builder.
     *
     * @param declaringClassName The name of a class with a {@code builder()} method that returns a configurable builder.
     * @param <T> The build target type.
     * @return The builder.
     * @throws IllegalArgumentException If the class cannot be instantiated or does not have a {@code builder()} method or
     * if that method does not return a {@link ConfigurableBuilder}.
     */
    @SuppressWarnings("unchecked")
    static <T> ConfigurableBuilder<T> newBuilder(final String declaringClassName) {
        try {
            final Class<?> declaringClass = Class.forName(declaringClassName);
            final Method builderMethod = declaringClass.getDeclaredMethod("builder");
            return (ConfigurableBuilder<T>) builderMethod.invoke(null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
    }
}
