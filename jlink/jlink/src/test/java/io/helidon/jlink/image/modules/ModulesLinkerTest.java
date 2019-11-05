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
import java.util.ArrayList;
import java.util.List;

import io.helidon.jlink.TestFiles;
import io.helidon.jlink.common.logging.Log;
import io.helidon.jlink.common.util.FileUtils;
import io.helidon.jlink.common.util.JavaRuntime;
import io.helidon.jlink.common.util.ProcessMonitor;

import org.junit.jupiter.api.Test;

import static io.helidon.jlink.common.util.FileUtils.CURRENT_JAVA_HOME_DIR;

/**
 * Unit test for class {@link ModulesLinker}.
 */
class ModulesLinkerTest {
    private static final Log LOG = Log.getLog("modules-linker-test");

    static Path link(Path mainJar, String variant) throws Exception {
        return link(CURRENT_JAVA_HOME_DIR, mainJar, variant);
    }

    static Path link(Path javaHome, Path mainJar, String variant) throws Exception {
        Path jreDirectory = mainJar.getParent().resolve(variant + "-modules-jre");
        Path targetDir = TestFiles.targetDir();
        Path classesDir = targetDir.resolve("classes");
        Path jandexJar = TestFiles.jandexJar();

        List<String> command = new ArrayList<>();
        command.add(JavaRuntime.javaCommand(javaHome).toString());
        command.add("-javaagent:" + TestFiles.agentJar());
        command.add("--module-path");
        command.add(classesDir.toString() + ":" + jandexJar.toString());
        command.add("--module");
        command.add("helidon.jlink/" + ModulesLinker.class.getName());
        command.add("--cds");
        command.add("true");
        command.add("--replace");
        command.add("true");
        command.add("--jre");
        command.add(jreDirectory.toString());
        command.add("--patches");
        command.add(TestFiles.patchesDir().toString());
        command.add(mainJar.toString());

        ProcessBuilder builder = new ProcessBuilder().directory(TestFiles.currentDir().toFile())
                                                     .command(command);
        ProcessMonitor.newMonitor(builder, "Building " + jreDirectory, LOG).run();
        LOG.info("\n\n");
        return jreDirectory;
    }

    @Test
    void testQuickstartSe() throws Exception {
        Path jre = link(TestFiles.helidonSeJar(), "se");
        FileUtils.assertDir(jre);
        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }

    @Test
    void testQuickstartMp() throws Exception {
        Path jre = link(TestFiles.helidonMpJar(), "mp");
        FileUtils.assertDir(jre);
        Path libDir = FileUtils.assertDir(jre.resolve("lib"));
        Path archiveFile = FileUtils.assertFile(libDir.resolve("server.jsa"));
    }
}
