/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http2;

import java.util.concurrent.TimeUnit;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Tracks whether the stream is waiting for an allocation to the stream flow control window, to the connection flow
 * control window or not waiting for an allocation and only issues allocation notifications when the stream is known to
 * be waiting for the notification.
 * <p>
 * It is possible for a stream to be waiting for a connection allocation when a stream allocation is made. Therefore
 * this class tracks the type of allocation that the stream is waiting for to ensure that notifications are correctly
 * triggered.
 * <p>
 * With the implementation at the time of writing, it is not possible for a stream to receive an unexpected connection
 * notification as these are only issues to streams in the backlog and a stream must be waiting for a connection
 * allocation in order to be placed on the backlog. However, as a precaution, this class protects against unexpected
 * connection notifications.
 * <p>
 * It is important for asynchronous processing not to notify unless a notification is expected else a dispatch will be
 * performed unnecessarily which may lead to unexpected results.
 * <p>
 * A previous implementation used separate locks for the stream and connection notifications. However, correct handling
 * of allocation waiting requires holding the stream lock when making the decision to wait. Therefore both allocations
 * need to wait on the Stream.
 */
class WindowAllocationManager {

    private static final Log log = LogFactory.getLog(WindowAllocationManager.class);
    private static final StringManager sm = StringManager.getManager(WindowAllocationManager.class);

    private static final int NONE = 0;
    private static final int STREAM = 1;
    private static final int CONNECTION = 2;

    private final Stream stream;

    private int waitingFor = NONE;

    WindowAllocationManager(Stream stream) {
        this.stream = stream;
    }

    void waitForStream(long timeout) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("windowAllocationManager.waitFor.stream", stream.getConnectionId(),
                    stream.getIdAsString(), Long.toString(timeout)));
        }

        waitFor(STREAM, timeout);
    }


    void waitForConnection(long timeout) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("windowAllocationManager.waitFor.connection", stream.getConnectionId(),
                    stream.getIdAsString(), Integer.toString(stream.getConnectionAllocationRequested()),
                    Long.toString(timeout)));
        }

        waitFor(CONNECTION, timeout);
    }


    void waitForStreamNonBlocking() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("windowAllocationManager.waitForNonBlocking.stream", stream.getConnectionId(),
                    stream.getIdAsString()));
        }

        waitForNonBlocking(STREAM);
    }


    void waitForConnectionNonBlocking() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("windowAllocationManager.waitForNonBlocking.connection", stream.getConnectionId(),
                    stream.getIdAsString()));
        }

        waitForNonBlocking(CONNECTION);
    }


    void notifyStream() {
        notify(STREAM);
    }


    void notifyConnection() {
        notify(CONNECTION);
    }


    void notifyAny() {
        notify(STREAM | CONNECTION);
    }


    boolean isWaitingForStream() {
        return isWaitingFor(STREAM);
    }


    boolean isWaitingForConnection() {
        return isWaitingFor(CONNECTION);
    }


    private boolean isWaitingFor(int waitTarget) {
        stream.windowAllocationLock.lock();
        try {
            return (waitingFor & waitTarget) > 0;
        } finally {
            stream.windowAllocationLock.unlock();
        }
    }


    private void waitFor(int waitTarget, final long timeout) throws InterruptedException {
        stream.windowAllocationLock.lock();
        try {
            if (waitingFor != NONE) {
                throw new IllegalStateException(sm.getString("windowAllocationManager.waitFor.ise",
                        stream.getConnectionId(), stream.getIdAsString()));
            }

            waitingFor = waitTarget;
            long startNanos = -1;

            // Loop to handle spurious wake-ups
            do {
                if (timeout < 0) {
                    stream.windowAllocationAvailable.await();
                } else {
                    long timeoutRemaining;
                    if (startNanos == -1) {
                        startNanos = System.nanoTime();
                        timeoutRemaining = timeout;
                    } else {
                        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                        if (elapsedMillis == 0) {
                            elapsedMillis = 1;
                        }
                        timeoutRemaining = timeout - elapsedMillis;
                        if (timeoutRemaining <= 0) {
                            return;
                        }
                    }
                    stream.windowAllocationAvailable.await(timeoutRemaining, TimeUnit.MILLISECONDS);
                }
            } while (waitingFor != NONE);
        } finally {
            stream.windowAllocationLock.unlock();
        }
    }


    private void waitForNonBlocking(int waitTarget) {
        stream.windowAllocationLock.lock();
        try {
            if (waitingFor == NONE) {
                waitingFor = waitTarget;
            } else if (waitingFor == waitTarget) {
                // NO-OP
                // Non-blocking post-processing may attempt to flush
            } else {
                throw new IllegalStateException(sm.getString("windowAllocationManager.waitFor.ise",
                        stream.getConnectionId(), stream.getIdAsString()));
            }
        } finally {
            stream.windowAllocationLock.unlock();
        }
    }


    private void notify(int notifyTarget) {

        stream.windowAllocationLock.lock();
        try {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("windowAllocationManager.notify", stream.getConnectionId(),
                        stream.getIdAsString(), Integer.toString(waitingFor), Integer.toString(notifyTarget)));
            }

            if ((notifyTarget & waitingFor) > NONE) {
                // Reset this here so multiple notifies (possible with a
                // backlog containing multiple streams and small window updates)
                // are handled correctly (only the first should trigger a call
                // to stream.notify(). Additional notify() calls may trigger
                // unexpected timeouts.
                waitingFor = NONE;
                Response response = stream.getCoyoteResponse();
                if (response != null) {
                    if (response.getWriteListener() == null) {
                        // Blocking, so use notify to release StreamOutputBuffer
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("windowAllocationManager.notified", stream.getConnectionId(),
                                    stream.getIdAsString()));
                        }
                        stream.windowAllocationAvailable.signal();
                    } else {
                        // Non-blocking so dispatch
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("windowAllocationManager.dispatched", stream.getConnectionId(),
                                    stream.getIdAsString()));
                        }
                        response.action(ActionCode.DISPATCH_WRITE, null);
                        // Need to explicitly execute dispatches on the StreamProcessor
                        // as this thread is being processed by an UpgradeProcessor
                        // which won't see this dispatch
                        response.action(ActionCode.DISPATCH_EXECUTE, null);
                    }
                }
            }
        } finally {
            stream.windowAllocationLock.unlock();
        }
    }
}
