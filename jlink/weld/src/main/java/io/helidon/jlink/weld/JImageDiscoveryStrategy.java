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

package io.helidon.jlink.weld;

import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.environment.deployment.discovery.jandex.JandexDiscoveryStrategy;
import org.jboss.weld.resources.spi.ResourceLoader;

/**
 * An {@link JImageDiscoveryStrategy} that assumes a Jandex index and can read from 'jrt://' urls.
 */
public class JImageDiscoveryStrategy extends JandexDiscoveryStrategy {
    static final int TOP_PRIORITY = 1000;

    private ResourceLoader resourceLoader;
    private Bootstrap bootstrap;
    private boolean scannerSet;

    public JImageDiscoveryStrategy() {
        super(null, null, null);
        System.out.println("JImageDiscoveryStrategy ctor");
        // TODO: consider using reflection to remove the handlers registered by the base class ctor
        // If do so, can explicitly register our handler(s) here rather than rely on the service
        // loader and priority mechanism. Don't forget to remove the META-NF/services entry ;-).
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        super.setResourceLoader(resourceLoader);
        this.resourceLoader = resourceLoader;
        setScanner();
    }

    @Override
    public void setBootstrap(Bootstrap bootstrap) {
        super.setBootstrap(bootstrap);
        this.bootstrap = bootstrap;
        setScanner();
    }

    private void setScanner() {
        if (!scannerSet && bootstrap != null && resourceLoader != null) {
            this.setScanner(new JImageBeanArchiveScanner(resourceLoader, bootstrap));
            scannerSet = true;
        }
    }
}
