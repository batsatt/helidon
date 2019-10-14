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

import java.net.URL;

import org.jboss.weld.bootstrap.api.Bootstrap;
import org.jboss.weld.environment.deployment.discovery.DefaultBeanArchiveScanner;
import org.jboss.weld.environment.logging.CommonLogger;
import org.jboss.weld.resources.spi.ResourceLoader;

/**
 * A {@link DefaultBeanArchiveScanner} that handles "jrt:/" archive references.
 */
public class JrtBeanArchiveScanner extends DefaultBeanArchiveScanner {
    private static final String JRT_URI_PROTOCOL = "jrt";
    static final String JRT_URI_PREFIX = JRT_URI_PROTOCOL + ":";
    private static final String JRT_URI_MODULES_PREFIX = JRT_URI_PREFIX + "/modules";
    private static final String SEP = "/";
    private static final char SEP_CHAR = '/';

    /**
     * Constructor.
     *
     * @param resourceLoader The resource loader.
     * @param bootstrap The bootstrap instance.
     */
    JrtBeanArchiveScanner(ResourceLoader resourceLoader, Bootstrap bootstrap) {
        super(resourceLoader, bootstrap);
        CommonLogger.LOG.tracef("%s active", getClass());
    }

    @Override
    protected String getBeanArchiveReference(URL url) {
        CommonLogger.LOG.tracef("%s mapping %s to archive ref", getClass(), url);
        if (url.getProtocol().equals(JRT_URI_PROTOCOL)) {
            // e.g. jrt:/jersey.weld2.se/META-INF/beans.xml
            String path = url.getPath();
            if (!path.startsWith(SEP)) {
                path = SEP + path;
            }
            int secondSep = path.indexOf(SEP_CHAR, 1);
            if (secondSep > 0) {
                String modulePath = path.substring(0, secondSep + 1);
                String archiveUrl = JRT_URI_MODULES_PREFIX + modulePath;
                CommonLogger.LOG.tracef("  archive ref", archiveUrl);
                return archiveUrl;
            } else {
                CommonLogger.LOG.warnf("%s malformed jrt url: %s", getClass(), url);
                return super.getBeanArchiveReference(url);
            }
        } else {
            CommonLogger.LOG.tracef("%s does not handle url: %s", getClass(), url);
           return super.getBeanArchiveReference(url);
        }
    }
}
