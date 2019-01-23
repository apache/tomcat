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

/**
 * Provides a mechanism for the Coyote connectors to communicate with the
 * {@link javax.servlet.AsyncContext}. It is implemented in this manner so that
 * the org.apache.coyote package does not have a dependency on the
 * org.apache.catalina package.
 */
public interface AsyncContextCallback {
    public void fireOnComplete();

    /**
     * Reports if the web application associated with this async request is
     * available.
     *
     * @return {@code true} if the associated web application is available,
     *         otherwise {@code false}
     */
    public boolean isAvailable();
}
