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

import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;

/**
 * Purpose of this class is to silently swallow any headers. It is used once
 * the connection close process has started if headers for new streams are
 * received.
 */
class HeaderSink implements HeaderEmitter {

    @Override
    public void emitHeader(String name, String value) {
        // NO-OP
    }

    @Override
    public void validateHeaders() throws StreamException {
        // NO-OP
    }

    @Override
    public void setHeaderException(StreamException streamException) {
        // NO-OP
        // The connection is already closing so no need to process additional
        // errors
    }
}
