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

public class Constants {

    // Prioritisation
    public static final int DEFAULT_WEIGHT = 16;

    // Parsing
    static final int DEFAULT_HEADER_READ_BUFFER_SIZE = 1024;

    // Limits
    static final int DEFAULT_MAX_COOKIE_COUNT = 200;
    static final int DEFAULT_MAX_HEADER_COUNT = 100;
    static final int DEFAULT_MAX_HEADER_SIZE = 8 * 1024;
    static final int DEFAULT_MAX_TRAILER_COUNT = 100;
    static final int DEFAULT_MAX_TRAILER_SIZE = 8 * 1024;
}
