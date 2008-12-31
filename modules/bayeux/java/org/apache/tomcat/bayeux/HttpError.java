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

package org.apache.tomcat.bayeux;

public class HttpError {
    private int code;
    private String status;
    private Throwable cause;
    public HttpError(int code, String status, Throwable cause) {
        this.code = code;
        this.status = status;
        this.cause = cause;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCause(Throwable exception) {
        this.cause = exception;
    }

    public int getCode() {
        return code;
    }

    public String getStatus() {
        return status;
    }

    public Throwable getCause() {
        return cause;
    }

    public String toString() {
        if (cause != null)
            return code + ":" + status + " - [" + cause + "]";
        else
            return code + ":" + status;
    }
}
