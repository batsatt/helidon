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

package io.helidon.common.configurable;

import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.NotificationEmitter;

import io.helidon.common.context.ContextAwareExecutorService;

/**
 * A {@link ThreadPoolExecutor} with an extensible growth policy and queue state accessors.
 */
public class ThreadPool extends ThreadPoolExecutor {
    private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());
    private static final int MIN_GROW_RATE = 1;
    private static final int MAX_GROW_RATE = 100;

    private final String name;
    private final WorkQueue queue;
    private final RejectionPolicy rejectionPolicy;
    private final AtomicInteger activeThreads;
    private final LongAdder totalActiveThreads;
    private final AtomicInteger completedTasks;
    private final AtomicInteger failedTasks;
    private final int growthThreshold;
    private final int growthRate;

    /**
     * Returns the given executor as a {@link ThreadPool} if possible.
     *
     * @param executor The executor.
     * @return The thread pool or empty if not a {@link ThreadPool}.
     */
    public static Optional<ThreadPool> asThreadPool(ExecutorService executor) {
        if (executor instanceof ThreadPool) {
            return Optional.of((ThreadPool) executor);
        } else if (executor instanceof ContextAwareExecutorService) {
            return asThreadPool(((ContextAwareExecutorService) executor).unwrap());
        }
        return Optional.empty();
    }

    /**
     * Returns a new {@code ThreadPool}.
     *
     * @param name The pool name.
     * @param corePoolSize The number of threads to keep in the pool, even if they are idle, unless
     * {@code allowCoreThreadTimeOut} is set
     * @param maxPoolSize The maximum number of threads to allow in the pool
     * @param growthThreshold The queue size above which pool growth should be considered if the pool is not fixed size.
     * @param growthRate The percentage of task submissions that should result in adding threads, expressed as a value
     * from 0 to 100. A rate of 0 means that the pool will never grow; for all other values the rate applies only when
     * all of the following are true:
     * <ul>
     * <li>the pool size is below the maximum, and</li>
     * <li>there are no idle threads, and</li>
     * <li>the number of tasks in the queue exceeds the {@code growthThreshold}, and</li>
     * <li>the queue size is constant or increasing</li>
     * </ul>
     * For example, a rate of 20 means that while these conditions are met one thread will be added for every 5 submitted tasks.
     * @param keepAliveMinutes When the number of threads is greater than the core, this is the maximum time that excess idle
     * threads will wait for new tasks before terminating.
     * @param workQueueCapacity The capacity of the work queue.
     * @param threadNamePrefix The name prefix to use when a new thread is created.
     * @param useDaemonThreads {@code true} if created threads should be set as daemon.
     * @param rejectionPolicy The rejection policy.
     * @throws IllegalArgumentException if any of the following holds:<br>
     * {@code name is null or empty}<br>
     * {@code corePoolSize < 0}<br>
     * {@code keepAliveMinutes < 0}<br>
     * {@code maximumPoolSize <= 0}<br>
     * {@code maximumPoolSize < corePoolSize}<br>
     * {@code growthThreshold < 0}<br>
     * {@code growthRate < 0} <br>
     * {@code growthRate > 100} <br>
     * {@code keepAliveMinutes < 1}
     * {@code workQueueCapacity < 1} <br>
     * {@code threadNamePrefix is null or empty}
     */
    static ThreadPool create(String name,
                             int corePoolSize,
                             int maxPoolSize,
                             int growthThreshold,
                             int growthRate,
                             long keepAliveMinutes,
                             int workQueueCapacity,
                             String threadNamePrefix,
                             boolean useDaemonThreads,
                             RejectionPolicy rejectionPolicy) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is null or empty");
        } else if (corePoolSize < 0) {
            throw new IllegalArgumentException("corePoolSize < 0");
        } else if (maxPoolSize < 0) {
            throw new IllegalArgumentException("maxPoolSize < 0");
        } else if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize < corePoolSize");
        } if (growthThreshold < 0) {
            throw new IllegalArgumentException("growthThreshold < 0");
        } else if (growthRate < 0) {
            throw new IllegalArgumentException("growthRate < 0");
        } else if (growthRate > MAX_GROW_RATE) {
            throw new IllegalArgumentException("growthRate > 100");
        } else if (keepAliveMinutes < 1) {
            throw new IllegalArgumentException("keepAliveMinutes < 1");
        } else if (workQueueCapacity < 1) {
            throw new IllegalArgumentException("workQueueCapacity < 1");
        } else if (threadNamePrefix == null || threadNamePrefix.isEmpty()) {
            throw new IllegalArgumentException("threadNamePrefix is null or empty");
        } else if (rejectionPolicy == null) {
            throw new IllegalArgumentException("rejectionPolicy is null");
        }

        final WorkQueue queue = createQueue(workQueueCapacity, corePoolSize, maxPoolSize, growthThreshold, growthRate);
        final Factory threadFactory = new Factory(name, threadNamePrefix, useDaemonThreads);
        return new ThreadPool(name, corePoolSize, maxPoolSize, growthThreshold, growthRate,
                              keepAliveMinutes, threadFactory, queue, rejectionPolicy);
    }

    private static WorkQueue createQueue(int capacity, int corePoolSize, int maxPoolSize, int growthThreshold, int growthRate) {
        if (maxPoolSize == corePoolSize || growthRate == 0) {
            return new WorkQueue(capacity);
        } else {
            final Predicate<ThreadPool> growthPolicy = new RateLimitGrowth(growthThreshold, growthRate);
            return new DynamicPoolQueue(growthPolicy, capacity, maxPoolSize);
        }
    }

    private ThreadPool(String name,
                       int corePoolSize,
                       int maximumPoolSize,
                       int growthThreshold,
                       int growthRate,
                       long keepAliveTime,
                       Factory threadFactory,
                       WorkQueue queue,
                       RejectionPolicy rejectionPolicy) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MINUTES, queue, threadFactory, rejectionPolicy);
        this.name = name;
        this.queue = queue;
        this.growthThreshold = growthThreshold;
        this.activeThreads = new AtomicInteger();
        this.totalActiveThreads = new LongAdder();
        this.completedTasks = new AtomicInteger();
        this.failedTasks = new AtomicInteger();
        this.growthRate = growthRate;
        this.rejectionPolicy = rejectionPolicy;
        queue.setPool(this);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(toString());
        }
    }

    /**
     * Returns the name of this pool.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the queue capacity.
     *
     * @return The capacity.
     */
    public int getQueueCapacity() {
        return queue.getCapacity();
    }

    /**
     * Returns the growth threshold.
     *
     * @return The threshold.
     */
    public int getGrowthThreshold() {
        return growthThreshold;
    }

    /**
     * Returns the growth rate.
     *
     * @return The rate.
     */
    public int getGrowthRate() {
        return growthRate;
    }

    /**
     * Returns the average queue size.
     *
     * @return The size.
     */
    public float getAverageQueueSize() {
        return queue.getAverageSize();
    }

    /**
     * Returns the peak queue size.
     *
     * @return The size.
     */
    public int getPeakQueueSize() {
        return queue.getPeakSize();
    }

    /**
     * Returns the number of completed tasks.
     *
     * @return The count.
     */
    public int getCompletedTasks() {
        return completedTasks.get();
    }

    /**
     * Returns the number of tasks that threw an exception.
     *
     * @return The count.
     */
    public int getFailedTasks() {
        return failedTasks.get();
    }

    /**
     * Returns the number of completed and failed tasks.
     *
     * @return The count.
     */
    public int getTotalTasks() {
        return completedTasks.get() + failedTasks.get();
    }

    /**
     * Returns the current number of active threads.
     *
     * @return The count.
     */
    public int getActiveThreads() {
        return activeThreads.get();
    }

    /**
     * Returns the average number of active threads.
     *
     * @return The average.
     */
    public float getAverageActiveThreads() {
        final float totalActive = totalActiveThreads.sum();
        if (totalActive == 0) {
            return 0.0f;
        } else {
            return totalActive / (float) getTotalTasks();
        }
    }

    /**
     * Returns the rejection count.
     *
     * @return The count.
     */
    public int getRejectionCount() {
        return rejectionPolicy.getRejectionCount();
    }

    /**
     * Tests whether or not the number of threads can change over time.
     *
     * @return {@code true} if maximum size is equal to core size.
     */
    public boolean isFixedSize() {
        return getMaximumPoolSize() == getCorePoolSize();
    }

    @Override
    public WorkQueue getQueue() {
        return queue;
    }

    /**
     * Returns the current number of tasks in the queue.
     *
     * @return The count.
     */
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler instanceof RejectionPolicy) {
            super.setRejectedExecutionHandler(handler);
        }
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "ThreadPool '" + getName() + "' {"
               + "corePoolSize=" + getCorePoolSize()
               + ", maxPoolSize=" + getMaximumPoolSize()
               + ", growthThreshold=" + getGrowthThreshold()
               + ", growthRate=" + getGrowthRate()
               + "%, queueCapacity=" + getQueueCapacity()
               + String.format(", averageQueueSize=%.2f", getAverageQueueSize())
               + ", peakQueueSize=" + getPeakQueueSize()
               + (isFixedSize() ? "" : String.format(", averageActiveThreads=%.2f", getAverageActiveThreads()))
               + (isFixedSize() ? "" : ", peakPoolSize=" + getLargestPoolSize())
               + ", completedTasks=" + getCompletedTasks()
               + ", failedTasks=" + getFailedTasks()
               + ", rejectedTasks=" + getRejectionCount()
               + '}';
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        activeThreads.incrementAndGet();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        completedTasks.incrementAndGet();
        totalActiveThreads.add(activeThreads.getAndDecrement());
    }

    @Override
    public void shutdown() {
        Event.write();
        super.shutdown();
    }

    /**
     * A {@link RejectedExecutionHandler} that adds the task to the queue; if that fails, the
     * rejection is counted and an exception thrown.
     */
    public static class RejectionPolicy implements RejectedExecutionHandler {
        private final AtomicInteger rejections = new AtomicInteger();

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            // Just add it to the queue if there is capacity
            final WorkQueue queue = ((ThreadPool) executor).getQueue();
            if (!queue.tryOffer(task)) {
                // No capacity, so reject
                LOGGER.warning("Task " + task + " rejected by " + executor);
                rejections.incrementAndGet();
                throwException(task, executor);
            }
        }

        /**
         * Returns the number of rejections.
         *
         * @return the count.
         */
        public int getRejectionCount() {
            return rejections.get();
        }

        /**
         * Throws an exception.
         *
         * @param task The task that is being rejected.
         * @param executor The executor that is rejecting the task.
         */
        protected void throwException(Runnable task, ThreadPoolExecutor executor) {
            throw new RejectedExecutionException("Task " + task + " rejected by " + executor);
        }
    }

    private static class Factory implements ThreadFactory {
        private final ThreadGroup group;
        private final String namePrefix;
        private final boolean useDaemonThreads;
        private final AtomicInteger threadCount;

        Factory(String groupName, String threadNamePrefix, boolean useDaemonThreads) {
            this.group = new ThreadGroup(groupName);
            this.namePrefix = threadNamePrefix;
            this.useDaemonThreads = useDaemonThreads;
            this.threadCount = new AtomicInteger();
        }

        @Override
        public Thread newThread(Runnable runnable) {
            final String name = namePrefix + threadCount.incrementAndGet();
            final Thread thread = new Thread(group, runnable, name);
            thread.setDaemon(useDaemonThreads);
            return thread;
        }
    }

    private static class WorkQueue extends LinkedBlockingQueue<Runnable> {
        private final int capacity;
        private final LongAdder totalQueueSizes;
        private final AtomicInteger totalTasks;
        private final AtomicInteger peakSize;

        WorkQueue(int capacity) {
            super(capacity);
            this.capacity = capacity;
            this.totalQueueSizes = new LongAdder();
            this.totalTasks = new AtomicInteger();
            this.peakSize = new AtomicInteger();
        }

        void setPool(ThreadPool pool) {
        }

        @Override
        public boolean offer(Runnable task) {
            return tryOffer(task);
        }

        boolean tryOffer(Runnable task) {
            if (super.offer(task)) {
                // Update stats
                final int queueSize = size();
                if (queueSize > peakSize.get()) {
                    peakSize.set(queueSize);
                }
                totalQueueSizes.add(queueSize);
                totalTasks.incrementAndGet();
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the capacity.
         *
         * @return The capacity.
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * Returns the average size.
         *
         * @return The size.
         */
        public float getAverageSize() {
            final float totalSize = totalQueueSizes.sum();
            if (totalSize == 0) {
                return 0.0f;
            } else {
                return totalSize / (float) totalTasks.get();
            }
        }

        /**
         * Returns the peak size.
         *
         * @return The size.
         */
        public int getPeakSize() {
            return peakSize.get();
        }
    }

    private static final class DynamicPoolQueue extends WorkQueue {
        private final Predicate<ThreadPool> growthPolicy;
        private final int maxPoolSize;
        // We can't make this final because it is a circular dependency, but we set it during the construction of
        // the pool itself and therefore don't have to worry about concurrent access.
        private ThreadPool pool;

        DynamicPoolQueue(Predicate<ThreadPool> growthPolicy, int capacity, int maxPoolSize) {
            super(capacity);
            this.maxPoolSize = maxPoolSize;
            this.growthPolicy = growthPolicy;
        }

        @Override
        void setPool(ThreadPool pool) {
            this.pool = pool;
        }

        @Override
        public boolean offer(Runnable task) {

            // Are we maxed out?

            final int currentSize = pool.getPoolSize();
            if (currentSize >= maxPoolSize) {

                // Yes, so enqueue if we can

                Event.add(Event.Type.MAX, pool, this);
                return tryOffer(task);

            } else if (pool.getActiveThreads() < currentSize) {

                // No, but we've got idle threads so enqueue if we can

                Event.add(Event.Type.IDLE, pool, this);
                return tryOffer(task);

            } else {

                // Ok, we might want to add a thread so ask our policy

                if (growthPolicy.test(pool)) {

                    // Add a thread. Note that this can still result in a rejection due to a race condition
                    // in which the pool has not yet grown from a previous false return (and so our maxPoolSize
                    // check above is not accurate); in this case, the rejection handler will just add it to
                    // the queue.

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Adding a thread, pool size = " + pool.getPoolSize() + ", queue size = " + size());
                    }
                    return false;

                } else {
                    // Enqueue if we can
                    return tryOffer(task);
                }
            }
        }
    }

    private static class RateLimitGrowth implements Predicate<ThreadPool> {
        private final AtomicInteger lastQueueSize;
        private final int queueThreshold;
        private final boolean alwaysRate;
        private final float rate;

        RateLimitGrowth(int queueThreshold, int growthRate) {
            this.lastQueueSize = new AtomicInteger();
            this.queueThreshold = queueThreshold;
            this.alwaysRate = growthRate == 100;
            this.rate = growthRate / 100f;
        }

        @Override
        public boolean test(ThreadPool pool) {
            final WorkQueue queue = pool.getQueue();
            final int queueSize = queue.size();

            // Is the queue above the threshold or will it be if we add another task?

            if (queueSize >= queueThreshold) {

                // Yes. Is it at or above the previous size?

                if (queueSize >= lastQueueSize.getAndSet(queueSize)) {

                    // Yes. Should we grow?
                    // Note that this random number generator is quite fast, and on average is faster than or equivalent to
                    // alternatives such as a counter (which does not provide even distribution) or System.nanoTime().

                    if (alwaysRate || ThreadLocalRandom.current().nextFloat() < rate) {

                        // Yep

                        Event.add(Event.Type.ADD, pool, queue);
                        return true;

                    } else {

                        // No, so don't grow yet

                        Event.add(Event.Type.WAIT, pool, queue);
                        return false;
                    }
                } else {

                    // Queue is draining, don't grow

                    Event.add(Event.Type.DRAIN, pool, queue);
                    return false;
                }
            } else {

                // Queue is below the threshold, don't grow

                Event.add(Event.Type.BELOW, pool, queue);
                return false;
            }
        }
    }

    // Consider removing this whole mechanism?

    private static class Event implements Comparable<Event> {
        private static final int MAX_EVENTS = getIntProperty("thread.pool.events", 0);
        private static final int DELAY_SECONDS = getIntProperty("thread.pool.events.delay", 0);
        private static final List<Event> EVENTS = new ArrayList<>(MAX_EVENTS);
        private static final AtomicBoolean STARTED = new AtomicBoolean();
        private static final AtomicBoolean WRITTEN = new AtomicBoolean();
        private static final long START_TIME = System.currentTimeMillis();
        private final long time;
        private final Type type;
        private final int threads;
        private final int activeThreads;
        private final int queueSize;

        enum Type {
            IDLE,
            MAX,
            BELOW,
            DRAIN,
            ADD,
            WAIT,
            GC
        }

        private Event(Type type, ThreadPool pool, WorkQueue queue) {
            this.time = System.currentTimeMillis();
            this.type = type;
            this.threads = pool.getPoolSize();
            this.activeThreads = pool.getActiveThreads();
            this.queueSize = queue.size();
        }

        @Override
        public int compareTo(Event o) {
            return Long.compare(time, o.time);
        }

        private String toCsv() {
            final float elapsedMillis = time - START_TIME;
            final float elapsedSeconds = elapsedMillis / 1000f;
            return String.format("%.4f,%s,%d,%d,%d\n", elapsedSeconds, type, threads, activeThreads, queueSize);
        }

        private static void add(Type type, ThreadPool pool, WorkQueue queue) {
            if (shouldAdd()) {
                if (!STARTED.getAndSet(true)) {
                    LOGGER.info("Recording up to " + MAX_EVENTS + " thread pool events");
                    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                        final NotificationEmitter emitter = (NotificationEmitter) bean;
                        emitter.addNotificationListener((notification, handback) -> {
                            if (notification.getType().equals("com.sun.management.gc.notification") && !WRITTEN.get()) {
                                add(Type.GC, pool, queue);
                            }
                        }, null, null);
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread(Event::write));
                }
                EVENTS.add(new Event(type, pool, queue));
            }
        }

        private static boolean shouldAdd() {
            if (EVENTS.size() < MAX_EVENTS) {
                if (DELAY_SECONDS == 0) {
                    return true;
                } else {
                    final long elapsedMillis = System.currentTimeMillis() - START_TIME;
                    return (elapsedMillis / 1000) >= DELAY_SECONDS;
                }
            }
            return false;
        }

        private static void write() {
            if (!EVENTS.isEmpty() && !WRITTEN.getAndSet(true)) {
                final Path file = Paths.get("thread-pool-events.csv").toAbsolutePath();
                LOGGER.info("Writing thread pool events to " + file);
                EVENTS.sort(null);
                try (OutputStream out = Files.newOutputStream(file,
                                                              StandardOpenOption.CREATE,
                                                              StandardOpenOption.WRITE,
                                                              StandardOpenOption.TRUNCATE_EXISTING)) {
                    out.write("Elapsed Seconds,Event,Threads,Active Threads,Queue Size\n".getBytes());
                    for (Event event : EVENTS) {
                        out.write(event.toCsv().getBytes());
                    }
                    LOGGER.info("Finished writing thread pool events");
                } catch (Throwable e) {
                    LOGGER.warning("failed to write thread pool events" + e);
                }
            }
        }

        private static int getIntProperty(String propertyName, int defaultValue) {
            final String value = System.getProperty(propertyName);
            return value == null ? defaultValue : Integer.parseInt(value);
        }
    }
}
