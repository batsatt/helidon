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

import io.helidon.jlink.common.logging.Log;

import static java.util.Objects.requireNonNull;

/**
 * Executes a process and waits for completion, monitoring and optionally logging the output.
 */
public class ProcessMonitor {
    private static final Log LOG = Log.getLog("processes");
    private static final String EOL = System.getProperty("line.separator");
    private static final ExecutorService EXECUTOR = ForkJoinPool.commonPool();
    private final ProcessBuilder builder;
    private final String description;
    private final Log executionLog;
    private final Log monitorLog;

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
        this.monitorLog = log;
    }

    /**
     * Starts the process and waits for completion.
     *
     * @throws Exception If the process fails.
     */
    public void run() throws Exception {
        executionLog.info(monitorLog == null ? description : (description + EOL));
        final Process process = builder.start();
        final StreamConsumer out = new StreamConsumer(process.getInputStream(), monitorLog);
        final StreamConsumer err = new StreamConsumer(process.getErrorStream(), monitorLog);
        final Future outFuture = EXECUTOR.submit(out);
        final Future errFuture = EXECUTOR.submit(err);
        int exitCode = process.waitFor();
        outFuture.cancel(true);
        errFuture.cancel(true);
        if (exitCode != 0) {
            final String message = description + " FAILED with exit code " + exitCode;
            executionLog.warn(message);
            if (monitorLog == null) {
                dump(out, "out", executionLog);
                dump(err, "err", executionLog);
            }
            throw new IllegalStateException(message);
        }
    }

    private void dump(StreamConsumer stream, String name, Log log) {
        final List<String> lines = stream.lines();
        if (!lines.isEmpty()) {
            log.info("--- process " + name + " ---");
            lines.forEach(log::info);
        }
    }

    private static class StreamConsumer implements Runnable {
        private final InputStream inputStream;
        private final List<String> lines;
        private final Log log;

        StreamConsumer(InputStream inputStream, Log log) {
            this.inputStream = inputStream;
            this.lines = new ArrayList<>();
            this.log = log;
        }

        List<String> lines() {
            return lines;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                                                                  .forEach(line -> {
                                                                      if (log != null) {
                                                                          log.info(line);
                                                                      }
                                                                      lines.add(line);
                                                                  });
        }
    }
}
