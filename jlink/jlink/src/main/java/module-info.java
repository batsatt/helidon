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
module helidon.jre {
    exports io.helidon.jre.modules.plugins;
    exports io.helidon.jre.common.logging;
    exports io.helidon.jre.common.util;

    requires jdk.jlink;
    requires java.instrument;
    requires java.logging;
    requires jandex;

    /* This doesn't work since 'provides' are processed before the required add-exports:

        provides jdk.tools.jlink.plugin.Plugin with io.helidon.tool.image.jlink.plugins.HelidonPlugin;

       Once jlink plugins are officially supported, the Agent won't be required
     */
}
