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
package org.apache.coyote;

public enum ErrorState {

    /**
     * Not in an error state.
     */
    NONE(false, 0, true, true),

    /**
     * The current request/response is in an error state and while it is safe to
     * complete the current response it is not safe to continue to use the
     * existing connection which must be closed once the response has been
     * completed.
     */
    CLOSE_CLEAN(true, 1, true, true),

    /**
     * The current request/response is in an error state and it is not safe to
     * continue to use them. For multiplexed protocols (such as HTTP/2) the
     * stream/channel must be closed immediately but the connection may
     * continue. For non-multiplexed protocols (AJP, HTTP/1.x) the current
     * connection must be closed.
     */
    CLOSE_NOW(true, 2, false, true),

    /**
     * An error has been detected that impacts the underlying network
     * connection. It is not safe to continue using the network connection which
     * must be closed immediately. For multiplexed protocols (such as HTTP/2)
     * this impacts all multiplexed channels.
     */
    CLOSE_CONNECTION_NOW(true, 3, false, false);

    private final boolean error;
    private final int severity;
    private final boolean ioAllowed;
    private final boolean connectionIoAllowed;

    private ErrorState(boolean error, int severity, boolean ioAllowed,
            boolean connectionIoAllowed) {
        this.error = error;
        this.severity = severity;
        this.ioAllowed = ioAllowed;
        this.connectionIoAllowed = connectionIoAllowed;
    }

    public boolean isError() {
        return error;
    }

    /**
     * Compare this ErrorState with the provided ErrorState and return the most
     * severe.
     *
     * @param input The error state to compare to this one
     *
     * @return The most severe error state from the the provided error state and
     *         this one
     */
    public ErrorState getMostSevere(ErrorState input) {
        if (input.severity > this.severity) {
            return input;
        } else {
            return this;
        }
    }

    public boolean isIoAllowed() {
        return ioAllowed;
    }

    public boolean isConnectionIoAllowed() {
        return connectionIoAllowed;
    }
}
