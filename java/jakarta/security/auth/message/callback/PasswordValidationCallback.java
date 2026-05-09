/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message.callback;

import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * A callback that enables an authentication module to supply a username
 * and password to the runtime and obtain the result of password validation.
 * The runtime validates the credentials and updates the {@code Subject}
 * if authentication succeeds.
 */
public class PasswordValidationCallback implements Callback {

    private final Subject subject;
    private final String username;
    private char[] password;
    private boolean result;

    /**
     * Creates a new {@code PasswordValidationCallback} with the specified
     * subject, username, and password.
     *
     * @param subject the {@code Subject} to be populated upon successful
     *        authentication
     * @param username the username to validate
     * @param password the password to validate
     */
    public PasswordValidationCallback(Subject subject, String username, char[] password) {
        this.subject = subject;
        this.username = username;
        this.password = password;
    }

    /**
     * Returns the {@code Subject} associated with this callback.  The
     * runtime populates this subject with authentication information
     * upon successful validation.
     *
     * @return the {@code Subject} for this callback
     */
    public Subject getSubject() {
        return subject;
    }

    /**
     * Returns the username to be validated.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password to be validated.
     *
     * @return the password character array
     */
    public char[] getPassword() {
        return password;
    }

    /**
     * Clears the password from memory for security purposes.  After
     * calling this method, {@link #getPassword()} will return an empty
     * character array.
     */
    public void clearPassword() {
        Arrays.fill(password, (char) 0);
        password = new char[0];
    }

    /**
     * Sets the result of the password validation performed by the runtime.
     *
     * @param result {@code true} if the password validation succeeded,
     *        {@code false} otherwise
     */
    public void setResult(boolean result) {
        this.result = result;
    }

    /**
     * Returns the result of the password validation.
     *
     * @return {@code true} if the password was validated successfully,
     *         {@code false} otherwise
     */
    public boolean getResult() {
        return result;
    }
}
