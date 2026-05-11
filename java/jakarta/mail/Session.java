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
package jakarta.mail;

import java.util.Properties;

/**
 * Represents a mail session configuration.
 * NOTE: This is a stub API, Apache Tomcat does not provide any implementation for this API.
 */
@SuppressWarnings("unused") // Dummy implementation
public class Session {
    /**
     * Default constructor.
     */
    public Session() {
    }

    /**
     * Returns a Session object with the given properties and authenticator.
     *
     * @param props the session properties
     * @param auth the authenticator
     * @return the mail session
     */
    public static Session getInstance(Properties props, Authenticator auth) {
        return null;
    }

    /**
     * Returns a Session object with the given properties.
     *
     * @param props the session properties
     * @return the mail session
     */
    public static Session getInstance(Properties props) {
        return null;
    }
}
