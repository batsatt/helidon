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

package io.helidon.jlink;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test jar utilities.
 */
public class TestJars {

    public static Path helidonSeJar() {
        final Path targetDir = Paths.get("/Users/batsatt/dev/helidon-quickstart-se/target");  // TODO generate via archetype?
        return targetDir.resolve("helidon-quickstart-se.jar");
    }

    public static Path helidonMpJar() {
        final Path targetDir = Paths.get("/Users/batsatt/dev/helidon-quickstart-mp/target");  // TODO generate via archetype?
        return targetDir.resolve("helidon-quickstart-mp.jar");
    }

    public static Path signedJar() {
        return Paths.get("/Users/batsatt/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.60/bcpkix-jdk15on-1.60.jar");  // TODO
    }

    public static Path weldJrtJar() {
        return Paths.get("/Users/batsatt/dev/helidon/jlink/weld/target/helidon-weld-jrt.jar"); // TODO use ProtectionDomain
    }
}
