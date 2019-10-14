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

package io.helidon.weld;

import javax.annotation.Priority;

import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.environment.deployment.discovery.jandex.JandexDiscoveryStrategy;
import org.jboss.weld.environment.logging.CommonLogger;
import org.jboss.weld.resources.spi.ResourceLoader;

/**
 * A {@link JandexDiscoveryStrategy} that adds support for archives in the module image file as 'jrt://' urls.
 * It relies on placing the {@link JrtBeanArchiveHandler} first in the list of handlers via the service loader
 * and {@link Priority} annotation mechanism; this handler supports jrt archives with or without indices.
 * <p>
 * The default handlers registered by the base class will process any archives not in the jrt image,
 * e.g. jars in the class-path or module-path.
 */
public class JrtDiscoveryStrategy extends JandexDiscoveryStrategy {
    static final int TOP_PRIORITY = 1000;

    private ResourceLoader resourceLoader;
    private Bootstrap bootstrap;
    private boolean scannerSet;

    /**
     * Constructor.
     */
    public JrtDiscoveryStrategy() {
        super(null, null, null);
        CommonLogger.LOG.tracef("%s active", getClass());
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
            this.setScanner(new JrtBeanArchiveScanner(resourceLoader, bootstrap));
            scannerSet = true;
        }
    }
}
