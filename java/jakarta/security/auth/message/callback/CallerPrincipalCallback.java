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

import java.security.Principal;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * Callback that enables an authentication module to inform the runtime of the caller principal or name of the
 * caller principal. The callback handler populates the given Subject with the authenticated principal, or returns
 * the principal/name that was provided.
 */
public class CallerPrincipalCallback implements Callback {

    private final Subject subject;
    private final Principal principal;
    private final String name;

    /**
     * Constructs a callback with a Subject and a Principal. The callback handler will add the principal to the
     * subject.
     *
     * @param subject   the subject to which the principal will be added
     * @param principal the principal representing the caller
     */
    public CallerPrincipalCallback(Subject subject, Principal principal) {
        this.subject = subject;
        this.principal = principal;
        this.name = null;
    }

    /**
     * Constructs a callback with a Subject and a caller name. The callback handler will create a principal from
     * the name and add it to the subject.
     *
     * @param subject the subject to which the principal will be added
     * @param name    the name of the caller
     */
    public CallerPrincipalCallback(Subject subject, String name) {
        this.subject = subject;
        this.principal = null;
        this.name = name;
    }

    /**
     * Returns the Subject associated with this callback.
     *
     * @return the Subject
     */
    public Subject getSubject() {
        return subject;
    }

    /**
     * Returns the Principal associated with this callback, or {@code null} if a name was provided instead.
     *
     * @return the Principal, or {@code null}
     */
    public Principal getPrincipal() {
        return principal;
    }

    /**
     * Returns the name associated with this callback, or {@code null} if a Principal was provided instead.
     *
     * @return the name, or {@code null}
     */
    public String getName() {
        return name;
    }
}
