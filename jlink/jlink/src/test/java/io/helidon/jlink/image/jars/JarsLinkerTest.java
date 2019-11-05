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

package io.helidon.jlink.image.jars;

import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.jlink.TestFiles;
import io.helidon.jlink.common.util.FileUtils;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for class {@link JarsLinker}.
 */
class JarsLinkerTest {

    @Test
    void testQuickstartSeNoCDS() throws Exception {
        Path mainJar = TestFiles.helidonSeJar();
        Path targetDir = mainJar.getParent();
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("se-jars-jre"))
                                            .mainJar(mainJar)
                                            .replace(true)
                                            .cds(false)
                                            .build();
        Path jre = JarsLinker.linker(config).link();

        FileUtils.assertDir(jre);
        Path appDir = FileUtils.assertDir(jre.resolve("app"));
        Path appLibDir = FileUtils.assertDir(appDir.resolve("libs"));

        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = libDir.resolve("server.jsa");
        assertThat(Files.exists(archiveFile), is(false));
    }

    @Test
    void testQuickstartSe() throws Exception {
        Path mainJar = TestFiles.helidonSeJar();
        Path targetDir = mainJar.getParent();
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("se-jars-jre"))
                                            .mainJar(mainJar)
                                            .replace(true)
                                            .verbose(false)
                                            .cds(true)
                                            .build();
        Path jre = JarsLinker.linker(config).link();

        FileUtils.assertDir(jre);
        Path appDir = FileUtils.assertDir(jre.resolve("app"));
        Path appLibDir = FileUtils.assertDir(appDir.resolve("libs"));

        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }

    @Test
    void testQuickstartMp() throws Exception {
        Path mainJar = TestFiles.helidonMpJar();
        Path targetDir = mainJar.getParent();
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("mp-jars-jre"))
                                            .mainJar(mainJar)
                                            .replace(true)
                                            .cds(true)
                                            .build();
        Path jre = JarsLinker.linker(config).link();

        FileUtils.assertDir(jre);
        Path appDir = FileUtils.assertDir(jre.resolve("app"));
        Path appLibDir = FileUtils.assertDir(appDir.resolve("libs"));

        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }
}
