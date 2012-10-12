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

/**
 * Interface between the HTTP upgrade process and the new protocol.
 *
 * @since Servlet 3.1
 */
public interface ProtocolHandler {

    /**
     * This method is called once the request/response pair where
     * {@link HttpServletRequest#upgrade(ProtocolHandler)} is called has
     * completed processing and is the point where control of the connection
     * passes from the container to the {@link ProtocolHandler}.
     *
     * @param connection    The connection that has been upgraded
     *
     * @since Servlet 3.1
     */
    void init(WebConnection connection);
}
