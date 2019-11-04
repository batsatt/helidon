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

import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.jlink.common.util.FileUtils;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for class {@link JarsLinker}.
 */
class JarsLinkerTest {

    //@Test
    void testSignedJar() {
        Path signed = Paths.get("/Users/batsatt/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.60/bcpkix-jdk15on-1.60.jar");  // TODO
        Jar jar = Jar.open(signed);
        assertThat(jar.isSigned(), is(true));
    }

    @Test
    void testQuickstartSe() throws Exception {
        Path targetDir = Paths.get("/Users/batsatt/dev/helidon-quickstart-se/target");  // TODO generate via archetype?
        Path applicationJar = targetDir.resolve("helidon-quickstart-se.jar");
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("se-jre"))
                                            .mainJar(applicationJar)
                                            .replace(true)
                                            .build();
        Path imageDir = JarsLinker.linker(config).link();

        FileUtils.assertDir(imageDir);
        Path appDir = FileUtils.assertDir(imageDir.resolve("app"));
        Path appLibDir = FileUtils.assertDir(appDir.resolve("libs"));

        Path libDir = FileUtils.assertDir(imageDir.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }

    @Test
    void testQuickstartMp() throws Exception {
        Path targetDir = Paths.get("/Users/batsatt/dev/helidon-quickstart-mp/target");   // TODO generate via archetype?
        Path applicationJar = targetDir.resolve("helidon-quickstart-mp.jar");
        Configuration config = Configuration.builder()
                                            .jreDirectory(targetDir.resolve("mp-jre"))
                                            .mainJar(applicationJar)
                                            .replace(true)
                                            .build();
        Path imageDir = JarsLinker.linker(config).link();

        FileUtils.assertDir(imageDir);
        Path appDir = FileUtils.assertDir(imageDir.resolve("app"));
        Path appLibDir = FileUtils.assertDir(appDir.resolve("libs"));

        Path libDir = FileUtils.assertDir(imageDir.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }
}
