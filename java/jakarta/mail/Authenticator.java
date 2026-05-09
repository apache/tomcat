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

/**
 * Abstract base class for providing user authentication when establishing network connections. Subclasses override
 * {@link #getPasswordAuthentication} to return the appropriate credentials. This class is part of the Jakarta Mail
 * API and follows the pattern defined by {@link java.net.Authenticator}.
 */
public class Authenticator {

    /**
     * Default constructor for subclasses.
     */
    public Authenticator() {
    }

    /**
     * Returns the password authentication information for the current request. The default implementation returns
     * {@code null}. Subclasses should override this method to provide actual credentials.
     *
     * @return the PasswordAuthentication object, or {@code null} if no authentication is available
     */
    protected PasswordAuthentication getPasswordAuthentication() {
        return null;
    }
}
