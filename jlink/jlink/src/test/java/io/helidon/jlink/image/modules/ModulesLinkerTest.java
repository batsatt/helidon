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

package io.helidon.jlink.image.modules;

import java.nio.file.Path;

import io.helidon.jlink.TestJars;
import io.helidon.jlink.common.util.FileUtils;

/**
 * Unit test for class {@link ModulesLinker}.
 */
class ModulesLinkerTest {

    // @Test TODO: must run external process so can use agent jar
    void testQuickstartSe() throws Exception {
        Path mainJar = TestJars.helidonSeJar();
        Path targetDir = mainJar.getParent();
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("se-modules-jre"))
                                            .mainJar(mainJar)
                                            .replace(true)
                                            .cds(true)
                                            .build();
        Path jre = ModulesLinker.linker(config).link();

        FileUtils.assertDir(jre);
        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }

    // @Test TODO: must run external process so can use agent jar
    void testQuickstartMp() throws Exception {
        Path mainJar = TestJars.helidonMpJar();
        Path targetDir = mainJar.getParent();
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("mp-modules-jre"))
                                            .mainJar(mainJar)
                                            .replace(true)
                                            .cds(true)
                                            .build();
        Path jre = ModulesLinker.linker(config).link();

        FileUtils.assertDir(jre);
        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }
}
