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

import java.io.Serial;

abstract class Http2Exception extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Http2Error error;


    Http2Exception(String msg, Http2Error error) {
        super(msg);
        this.error = error;
    }


    Http2Exception(String msg, Http2Error error, Throwable cause) {
        super(msg, cause);
        this.error = error;
    }


    Http2Error getError() {
        return error;
    }
}
