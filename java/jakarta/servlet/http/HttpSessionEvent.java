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
package jakarta.servlet.http;

/**
 * This is the class representing event notifications for changes to sessions within a web application.
 *
 * @since Servlet 2.3
 */
public class HttpSessionEvent extends java.util.EventObject {
    private static final long serialVersionUID = 1L;

    /**
     * Construct a session event from the given source.
     *
     * @param source The HTTP session where the change took place
     */
    public HttpSessionEvent(HttpSession source) {
        super(source);
    }

    /**
     * Get the session that changed.
     *
     * @return The session that changed
     */
    public HttpSession getSession() {
        return (HttpSession) super.getSource();
    }
}
