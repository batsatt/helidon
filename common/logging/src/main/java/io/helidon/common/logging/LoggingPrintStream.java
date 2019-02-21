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

package io.helidon.common.logging;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A PrintStream that writes to a {@link Logger}.
 */
public class LoggingPrintStream extends PrintStream {
    private static final String ENCODING = "ISO-8859-1";
    private static final int LINE_FEED = 10;
    private static final int CARRIAGE_RETURN = 13;
    private static final int NONE = -1;
    private final ByteArrayOutputStream buffer;
    private final Logger logger;
    private final Level level;
    private int last;

    /**
     * Constructor.
     *
     * @param logger The logger to write to.
     * @param level The level at which to write log records.
     * @throws UnsupportedEncodingException If the ISO_8859_1 encoding is not supported.
     */
    public LoggingPrintStream(final Logger logger, final Level level) throws UnsupportedEncodingException {
        super(new ByteArrayOutputStream(), true, ENCODING);
        this.level = level;
        this.buffer = (ByteArrayOutputStream) super.out;
        this.logger = logger;
        this.last = NONE;

    }

    @Override
    public void write(final int b) {
        if (this.last == CARRIAGE_RETURN && b == LINE_FEED) {
            this.last = NONE;
        } else {
            if (b != LINE_FEED && b != CARRIAGE_RETURN) {
                super.write(b);
            } else {
                try {
                    if (logger.isLoggable(level)) {
                        logger.log(level, buffer.toString(ENCODING));
                    }
                } catch (UnsupportedEncodingException ignore) {
                } finally {
                    this.buffer.reset();
                }
            }
            this.last = b;
        }
    }

    @Override
    public void write(final byte[] bytes, int offset, int length) {
        if (length >= 0) {
            for (int index = 0; index < length; ++index) {
                this.write(bytes[offset + index]);
            }
        } else {
            throw new ArrayIndexOutOfBoundsException(length);
        }
    }

    @Override
    public String toString() {
        return logger.getName();
    }
}
