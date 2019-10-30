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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.jlink.common.logging.Log;

import static io.helidon.jlink.common.util.FileUtils.assertDir;
import static io.helidon.jlink.common.util.FileUtils.listFiles;
import static java.util.Objects.requireNonNull;

/**
 * A Helidon application.
 */
public class Application {
    private static final Log LOG = Log.getLog("application");
    private static final String LIBS_DIR = "libs";
    private static final String JAR_SUFFIX = ".jar";
    private static final String MP_FILE_PREFIX = "helidon-microprofile";
    private final JavaHome targetJdk;
    private final Jar appJar;
    private final List<Jar> libJars;
    private final boolean isMicroprofile;

    public Application(JavaHome targetJdk, Path applicationJar) {
        this.targetJdk = requireNonNull(targetJdk);
        final Path libDir = assertDir(applicationJar.getParent().resolve(LIBS_DIR));
        final List<Path> libs = listFiles(libDir, fileName -> fileName.endsWith(JAR_SUFFIX));
        this.isMicroprofile = libs.stream().anyMatch(e -> e.getFileName().toString().startsWith(MP_FILE_PREFIX));
        this.appJar = new Jar(applicationJar, isMicroprofile);
        this.libJars = listFiles(libDir, fileName -> fileName.endsWith(JAR_SUFFIX)).stream()
                                                                                   .map(path -> new Jar(path, isMicroprofile))
                                                                                   .collect(Collectors.toList());
    }

    public JavaHome targetJdk() {
        return targetJdk;
    }

    public boolean isMicroprofile() {
        return isMicroprofile;
    }

    public Jar applicationJar() {
        return appJar;
    }

    public List<Jar> applicationLibJars() {
        return libJars;
    }

    public Stream<Jar> jars() {
        return Stream.concat(Stream.of(appJar), libJars.stream());
    }
}
