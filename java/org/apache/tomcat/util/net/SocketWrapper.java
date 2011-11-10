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

public class SocketWrapper<E> {

    protected volatile E socket;

    protected volatile long lastAccess = -1;
    protected long timeout = -1;
    protected boolean error = false;
    protected long lastRegistered = 0;
    protected volatile int keepAliveLeft = 100;
    protected boolean async = false;
    protected boolean keptAlive = false;

    public SocketWrapper(E socket) {
        this.socket = socket;
    }

    public E getSocket() {
        return socket;
    }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
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
}
