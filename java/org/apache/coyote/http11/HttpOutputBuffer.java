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
package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.OutputBuffer;

public interface HttpOutputBuffer extends OutputBuffer {

    /**
     * Finish writing the current response. It is acceptable to write extra
     * bytes using {@link #doWrite(java.nio.ByteBuffer)} during the execution of
     * this method.
     *
     * @throws IOException If an I/O error occurs while writing to the client
     */
    public void end() throws IOException;

    /**
     * Flushes any unwritten data to the client.
     *
     * @throws IOException If an I/O error occurs while flushing
     */
    public void flush() throws IOException;
}
