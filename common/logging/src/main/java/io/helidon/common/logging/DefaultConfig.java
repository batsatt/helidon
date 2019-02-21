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

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.config.Config;

/**
 * Caching supplier for a default constructed {@link Config} instance.
 * TODO: this functionality should be part of base config
 * TODO: an MP specific subclass should be created using ConfigProviderResolver.instance().getConfig(classLoader)
 *
 * @since 2019-02-01
 */
public class DefaultConfig implements Supplier<Config> {
    private static final LazySoftReference<Config> REFERENCE = new LazySoftReference<>(load());

    /**
     * Returns the cached default constructed {@link Config} instance.
     *
     * @return The config.
     */
    public static Config config() {
        return REFERENCE.get();
    }

    /**
     * Get the default config instance.
     *
     * @return The instance.
     */
    @Override
    public Config get() { // TODO: subclass in MP and use ConfigProviderResolver.instance().getConfig(classLoader)
        return Config.create();
    }

    private static DefaultConfig load() {
        final Iterator<DefaultConfig> iter = ServiceLoader.load(DefaultConfig.class).iterator();
        if (iter.hasNext()) {
            return iter.next();
        } else {
            return new DefaultConfig();
        }
    }
}
