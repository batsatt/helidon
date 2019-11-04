package io.helidon.jlink.image.modules.plugins;/*
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * Test generated SystemModules classes.
 */
class SystemModulesTest {
    // VM Options: -ea --add-exports=java.base/jdk.internal.module=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/jdk.internal.module=ALL-UNNAMED --add-reads=UNNAMED=java.base
    // @Test
    public void testSystemModules() throws Exception {

        // TODO: switch to read from generated image !!

        jdk.internal.module.ModuleBootstrap bootstrap = null; // Just to force the --add-export
        Path rootDir = Paths.get("classes");
        ClassLoader loader = new LocalOnlyLoader(rootDir);
        Object modules = newInstance(loader, "jdk.internal.module.SystemModules$all");
        System.out.println("got system modules");

        Method moduleReadsMethod = getMethod(modules, "moduleReads");
        Map<String, Set<String>> reads = (Map<String, Set<String>>) moduleReadsMethod.invoke(modules);
        System.out.println("got reads!!");
        String authCdi = "io.helidon.microprofile.jwt.auth.cdi";
        String expectedRead = "microprofile.jwt.auth.api";
        Set<String> authReads = reads.get(authCdi);
        System.out.println();
        System.out.println(authCdi + " reads " + authReads);
        System.out.println(authCdi + (authReads.contains(expectedRead) ? " DOES read " : " does NOT read ") + expectedRead);
        System.out.println();


        Method descriptorsMethod = getMethod(modules, "moduleDescriptors");
        ModuleDescriptor[] descriptors = (ModuleDescriptor[]) descriptorsMethod.invoke(modules);
        System.out.println("got descriptors!!");

        Object map = newInstance(loader, "jdk.internal.module.SystemModulesMap");
        System.out.println("got map!!");

        String[] moduleNames = (String[]) getMethod(map, "moduleNames").invoke(null);
        String[] classNames = (String[]) getMethod(map, "classNames").invoke(null);
        System.out.println("got names!!");

    }

    static Object newInstance(ClassLoader loader, String className) throws Exception {
        Class<?> clz = loader.loadClass(className);
        System.out.println("got class " + clz);
        Constructor<?> ctor = clz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object object = ctor.newInstance();
        System.out.println("got instance " + object);
        return object;
    }

    static Method getMethod(Object obj, String methodName) throws Exception {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        System.out.println("got method " + method);
        return method;
    }

    static class LocalOnlyLoader extends ClassLoader {
        private final Path rootDir;

        LocalOnlyLoader(Path rootDir) {
            super(LocalOnlyLoader.class.getClassLoader());
            this.rootDir = rootDir;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = findClass(name);
            }
            if (c == null) {
                return super.loadClass(name, resolve);
            } else {
                return c;
            }
        }

        @Override
        public Class findClass(String name) {
            String path = name.replace('.', File.separatorChar) + ".class";
            Path file = rootDir.resolve(path);
            if (Files.exists(file)) {
                byte[] data = loadClassData(file);
                return defineClass(name, data, 0, data.length);
            } else if (name.startsWith("jdk.internal.module.")) {
                InputStream in = getClass().getClassLoader().getResourceAsStream(path);
                if (in != null) {
                    byte[] data = loadClassData(in);
                    return defineClass(name, data, 0, data.length);
                }
            }
            return null;
        }

        private byte[] loadClassData(Path file) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private byte[] loadClassData(InputStream in) {
            byte[] buffer;
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            int nextValue = 0;
            try {
                while ((nextValue = in.read()) != -1) {
                    byteStream.write(nextValue);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            buffer = byteStream.toByteArray();
            return buffer;
        }
    }
}
