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

package io.helidon.jre.modules.plugins;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.helidon.jre.TestFiles;
import io.helidon.jre.common.util.ClassDataSharing;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unit test for class {@link ClassDataSharing}.
 */
class ClassDataSharingTest {
    private static final Path JAVA_HOME = Paths.get(System.getProperty("java.home"));

    @Test
    @Disabled // TODO: weldJrtJar is not available in maven build at this point!
    void testQuickstartMp() throws Exception {
        Path mainJar = TestFiles.helidonMpJar();
        Path weldJrtJar = TestFiles.weldJrtJar();
        Path archiveFile = Files.createTempFile("server","jsa");
        ClassDataSharing cds = ClassDataSharing.builder()
                                               .jre(JAVA_HOME)
                                               .applicationJar(mainJar)
                                               .createArchive(false)
                                               .logOutput(false)
                                               .build();
        assertThat(cds, is(not(nullValue())));
        assertThat(cds.classList(), is(not(nullValue())));
        assertThat(cds.classList(), is(not(empty())));
        assertThat(cds.applicationJar(), is(not(nullValue())));
        assertThat(cds.classListFile(), is(not(nullValue())));
        assertThat(cds.archiveFile(), is(nullValue()));

        assertContains(cds.classList(), "org/jboss/weld/environment/deployment/discovery/BeanArchiveScanner");
        assertDoesNotContain(cds.classList(), "io/helidon/weld/JrtBeanArchiveHandler");
        assertDoesNotContain(cds.classList(), "io/helidon/weld/JrtBeanArchiveScanner");
        assertDoesNotContain(cds.classList(), "io/helidon/weld/JrtDiscoveryStrategy");

        cds = ClassDataSharing.builder()
                              .jre(JAVA_HOME)
                              .applicationJar(mainJar)
                              .classListFile(cds.classListFile())
                              .archiveFile(archiveFile)
                              .weldJrtJar(weldJrtJar)
                              .logOutput(false)
                              .build();

        assertThat(cds.classListFile(), is(not(nullValue())));

        assertContains(cds.classList(), "org/jboss/weld/environment/deployment/discovery/BeanArchiveScanner");
        assertContains(cds.classList(), "io/helidon/weld/JrtBeanArchiveHandler");
        assertContains(cds.classList(), "io/helidon/weld/JrtBeanArchiveScanner");
        assertContains(cds.classList(), "io/helidon/weld/JrtDiscoveryStrategy");

        Path archive = cds.archiveFile();
        assertThat(archive, is(not(nullValue())));
        assertThat(Files.exists(archive), is(true));
        assertThat(Files.isRegularFile(archive), is(true));
    }

    private static void assertContains(List<String> list, String value) {
        assertThat(list.indexOf(value), is(greaterThan(-1)));
    }

    private static void assertDoesNotContain(List<String> list, String value) {
        assertThat(list.indexOf(value), is(-1));
    }
}
