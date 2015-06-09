/**
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
package javax.security.auth.message.callback;

import java.util.Arrays;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * Callback that enables an authentication module to supply a user name and
 * password (to a runtime?) and determine if the result of validation.
 */
public class PasswordValidationCallback implements Callback {

    private final Subject subject;
    private final String username;
    private char[] password;
    private boolean result;

    public PasswordValidationCallback(Subject subject, String username, char[] password) {
        this.subject = subject;
        this.username = username;
        this.password = password;
    }

    public Subject getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    public void clearPassword() {
        Arrays.fill(password, (char) 0);
        password = new char[0];
    }

    public boolean getResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }
}
