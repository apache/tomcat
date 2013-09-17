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
package org.apache.tomcat.util.net;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class SocketWrapper<E> {

    private volatile E socket;

    private volatile long lastAccess = -1;
    private long timeout = -1;
    private boolean error = false;
    private volatile int keepAliveLeft = 100;
    private boolean comet = false;
    private boolean async = false;
    private boolean keptAlive = false;
    private boolean upgraded = false;
    /*
     * Following cached for speed / reduced GC
     */
    private String localAddr = null;
    private String localName = null;
    private int localPort = -1;
    private String remoteAddr = null;
    private String remoteHost = null;
    private int remotePort = -1;
    /*
     * Used if block/non-blocking is set at the socket level. The client is
     * responsible for the thread-safe use of this field via the locks provided.
     */
    private volatile boolean blockingStatus = true;
    private final Lock blockingStatusReadLock;
    private final WriteLock blockingStatusWriteLock;

    /*
     * In normal servlet processing only one thread is allowed to access the
     * socket at a time. That is controlled by a lock on the socket for both
     * read and writes). When HTTP upgrade is used, one read thread and one
     * write thread are allowed to access the socket concurrently. In this case
     * the lock on the socket is used for reads and the lock below is used for
     * writes.
     */
    private final Object writeThreadLock = new Object();

    private Set<DispatchType> dispatches = new LinkedHashSet<>();

    /*
     * Used to indicate that the socket is in the process of closing / has been
     * closed. Once this flag has been set, no further reads or writes should
     * take place. Its primary purpose is with upgraded connections where a
     * socket may be in use in application code with no immediate way to signal
     * that the socket is no longer valid. Checking this flag before any
     * application triggered read or write will enable an IOException to be
     * thrown.
     */
    private boolean closing = false;

    public SocketWrapper(E socket) {
        this.socket = socket;
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.blockingStatusReadLock = lock.readLock();
        this.blockingStatusWriteLock = lock.writeLock();
    }

    public E getSocket() {
        return socket;
    }

    public boolean isComet() { return comet; }
    public void setComet(boolean comet) { this.comet = comet; }
    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public long getLastAccess() { return lastAccess; }
    public void access() { access(System.currentTimeMillis()); }
    public void access(long access) { lastAccess = access; }
    public void setTimeout(long timeout) {this.timeout = timeout;}
    public long getTimeout() {return this.timeout;}
    public boolean getError() { return error; }
    public void setError(boolean error) { this.error = error; }
    public void setKeepAliveLeft(int keepAliveLeft) { this.keepAliveLeft = keepAliveLeft;}
    public int decrementKeepAlive() { return (--keepAliveLeft);}
    public boolean isKeptAlive() {return keptAlive;}
    public void setKeptAlive(boolean keptAlive) {this.keptAlive = keptAlive;}
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) {this.localPort = localPort; }
    public String getLocalName() { return localName; }
    public void setLocalName(String localName) {this.localName = localName; }
    public String getLocalAddr() { return localAddr; }
    public void setLocalAddr(String localAddr) {this.localAddr = localAddr; }
    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) {this.remotePort = remotePort; }
    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) {this.remoteHost = remoteHost; }
    public String getRemoteAddr() { return remoteAddr; }
    public void setRemoteAddr(String remoteAddr) {this.remoteAddr = remoteAddr; }
    public boolean getBlockingStatus() { return blockingStatus; }
    public void setBlockingStatus(boolean blockingStatus) {
        this.blockingStatus = blockingStatus;
    }
    public Lock getBlockingStatusReadLock() { return blockingStatusReadLock; }
    public WriteLock getBlockingStatusWriteLock() {
        return blockingStatusWriteLock;
    }
    public Object getWriteThreadLock() { return writeThreadLock; }
    public void addDispatch(DispatchType dispatchType) {
        dispatches.add(dispatchType);
    }
    public boolean hasNextDispatch() {
        return dispatches.size() > 0;
    }
    public DispatchType getNextDispatch() {
        DispatchType result = null;
        Iterator<DispatchType> iter = dispatches.iterator();
        if (iter.hasNext()) {
            result = iter.next();
            iter.remove();
        }
        return result;
    }
    public void clearDispatches() {
        dispatches.clear();
    }
    public boolean isClosing() { return closing; }
    public void setClosing(boolean closing) { this.closing = closing; }

    public void reset(E socket, long timeout) {
        async = false;
        blockingStatus = true;
        closing = false;
        comet = false;
        dispatches.clear();
        error = false;
        keepAliveLeft = 100;
        lastAccess = System.currentTimeMillis();
        localAddr = null;
        localName = null;
        localPort = -1;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        this.socket = socket;
        this.timeout = timeout;
        upgraded = false;
    }
}
