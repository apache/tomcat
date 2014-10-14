/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.servlet.http;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

/**
 * The interface used by a {@link HttpUpgradeHandler} to interact with an upgraded
 * HTTP connection.
 *
 * @since Servlet 3.1
 */
public interface WebConnection extends AutoCloseable {

    /**
     * Provides access to the {@link ServletInputStream} for reading data from
     * the client.
     *
     * @return the input stream
     *
     * @throws IOException If an I/O occurs while obtaining the stream
     */
    ServletInputStream getInputStream() throws IOException;

    /**
     * Provides access to the {@link ServletOutputStream} for writing data to
     * the client.
     *
     * @return the output stream
     *
     * @throws IOException If an I/O occurs while obtaining the stream
     */
    ServletOutputStream getOutputStream() throws IOException;
}