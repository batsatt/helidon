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

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A reference whose value is cached a {@link SoftReference} and initialized or refreshed via a {@link Supplier}.
 *
 * @since 2019-02-02
 */
public class LazySoftReference<V> {
    private final AtomicReference<SoftReference<V>> reference;
    private final Supplier<V> supplier;

    /**
     * Constructor.
     *
     * @param valueSupplier A supplier for the value, used by {@link #get()} to fetch the initial
     * value or to refresh it if the previous value has been garbage collected.
     */
    public LazySoftReference(final Supplier<V> valueSupplier) {
        // Create a useless soft reference instance here in order to streamline get()
        this.reference = new AtomicReference<>(new SoftReference<>(null));
        this.supplier = valueSupplier;
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final V get() {
        V value = reference.get().get();
        if (value == null) {
            value = refreshValue();
        }
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(get());
    }

    private V refreshValue() {
        synchronized (reference) {
            final V value = supplier.get();
            reference.set(new SoftReference<>(value));
            return value;
        }
    }
}
