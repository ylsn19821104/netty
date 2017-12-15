/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allows a way to register some {@link Runnable} that will executed once there are no references to the {@link Thread}
 * anymore. This typically happens once the {@link Thread} dies / completes.
 */
public final class ThreadCleaner {

    // This will hold a reference to the ThreadCleanerReference which will be removed once we called cleanup()
    private static final Set<ThreadCleanerReference> LIVE_SET = new ConcurrentSet<ThreadCleanerReference>();
    private static final ReferenceQueue<Object> REFERENCE_QUEUE = new ReferenceQueue<Object>();
    private static final AtomicBoolean CLEANER_RUNNING = new AtomicBoolean(false);

    private static final Runnable CLEANER_TASK = new Runnable() {
        @Override
        public void run() {
            boolean interrupted = false;
            for (;;) {
                // Keep on processing as long as the LIVE_SET is not empty and once it becomes empty
                // See if we can let this thread complete.
                while (!LIVE_SET.isEmpty()) {
                    try {
                        ThreadCleanerReference reference =
                                (ThreadCleanerReference) REFERENCE_QUEUE.remove();
                        try {
                            reference.cleanup();
                        } finally {
                            LIVE_SET.remove(reference);
                        }
                    } catch (InterruptedException ex) {
                        // Just consume and move on
                        interrupted = true;
                    }
                }
                CLEANER_RUNNING.set(false);

                // Its important to first access the LIVE_SET and then CLEANER_RUNNING to ensure correct
                // behavior in multi-threaded environments.
                if (LIVE_SET.isEmpty() || !CLEANER_RUNNING.compareAndSet(false, true)) {
                    // There was nothing added after we set STARTED to false or some other cleanup Thread
                    // was started already so its safe to let this Thread complete now.
                    break;
                }
            }
            if (interrupted) {
                // As we catched the InterruptedException above we should mark the Thread as interrupted.
                Thread.currentThread().interrupt();
            }
        }
    };

    /**
     * Register the given {@link Thread} for which the {@link Runnable} will be executed once there are no references
     * to the object anymore, which typically happens once the {@link Thread} dies.
     *
     * This should only be used if there are no other ways to execute some cleanup once the {@link Thread} dies as
     * it is not a cheap way to handle the cleanup.
     */
    public static void register(Thread thread, Runnable cleanupTask) {
        ThreadCleanerReference reference = new ThreadCleanerReference(thread,
                ObjectUtil.checkNotNull(cleanupTask, "cleanupTask"));
        // Its important to add the reference to the LIVE_SET before we access CLEANER_RUNNING to ensure correct
        // behavior in multi-threaded environments.
        LIVE_SET.add(reference);

        // Check if there is already
        if (CLEANER_RUNNING.compareAndSet(false, true)) {
            Thread cleanupThread = new Thread(CLEANER_TASK);
            cleanupThread.setPriority(Thread.MIN_PRIORITY);
            // Set to null to ensure we not create classloader leaks by holding a strong reference to the inherited
            // classloader.
            // See:
            // - https://github.com/netty/netty/issues/7290
            // - https://bugs.openjdk.java.net/browse/JDK-7008595
            cleanupThread.setContextClassLoader(null);
            cleanupThread.setName("ThreadCleanerReaper");

            // This Thread is not a daemon as it will die once all references to the registered Threads will go away
            // and its important to always invoke all cleanup tasks as these may free up memory etc.
            cleanupThread.setDaemon(true);
            cleanupThread.start();
        }
    }

    private ThreadCleaner() {
        // Only contains a static method.
    }

    private static final class ThreadCleanerReference extends WeakReference<Thread> {
        private final Runnable cleanupTask;

        ThreadCleanerReference(Thread referent, Runnable cleanupTask) {
            super(referent, REFERENCE_QUEUE);
            this.cleanupTask = cleanupTask;
        }

        void cleanup() {
            cleanupTask.run();
        }

        @Override
        public Thread get() {
            return null;
        }

        @Override
        public void clear() {
            LIVE_SET.remove(this);
            super.clear();
        }
    }
}
