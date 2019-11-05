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

package io.helidon.jlink.common.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import io.helidon.jlink.common.logging.Log;

import static java.util.Objects.requireNonNull;

/**
 * Executes a process and waits for completion, monitoring and optionally logging the combined output.
 */
public class ProcessMonitor {
    private static final Log LOG = Log.getLog("processes");
    private static final String EOL = System.getProperty("line.separator");
    private static final ExecutorService EXECUTOR = ForkJoinPool.commonPool();
    private final ProcessBuilder builder;
    private final String description;
    private final Log executionLog;
    private final boolean capturing;
    private final List<String> capturedOutput;
    private final Consumer<String> processOutput;

    /**
     * Returns a new monitor for the given {@link ProcessBuilder}.
     *
     * @param builder The builder, which must be ready to start.
     * @param description A description of the process.
     * @param log Log to write process output to. May be {@code null} if output should only be logged on failure.
     * @return The monitor.
     */
    public static ProcessMonitor newMonitor(ProcessBuilder builder, String description, Log log) {
        return new ProcessMonitor(builder, description, log);
    }

    private ProcessMonitor(ProcessBuilder builder, String description, Log log) {
        this.builder = requireNonNull(builder);
        this.description = requireNonNull(description);
        this.executionLog = log == null ? LOG : log;
        this.capturedOutput = new ArrayList<>();
        if (log == null) {
            capturing = true;
            processOutput = capturedOutput::add;
        } else {
            capturing = false;
            processOutput = line -> log.info(line);
        }
    }

    /**
     * Starts the process and waits for completion.
     *
     * @throws Exception If the process fails.
     */
    public void run() throws Exception {
        executionLog.info(capturing ? description : (description + EOL));
        builder.redirectErrorStream(true); // Merge streams
        final Process process = builder.start();
        final Future output = EXECUTOR.submit(new StreamConsumer(process.getInputStream(), processOutput));
        int exitCode = process.waitFor();
        output.cancel(true);
        if (exitCode != 0) {
            final StringBuilder message = new StringBuilder();
            message.append(description).append(" failed with exit code ").append(exitCode);
            if (capturing) {
                message.append(EOL);
                capturedOutput.forEach(line -> message.append("    ").append(line).append(EOL));
            }
            throw new Error(message.toString());
        }
    }

    private static class StreamConsumer implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> output;

        StreamConsumer(InputStream inputStream, Consumer<String> output) {
            this.inputStream = inputStream;
            this.output = output;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                                                                  .forEach(output);
        }
    }
}
