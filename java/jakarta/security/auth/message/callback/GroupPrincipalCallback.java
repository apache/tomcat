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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

/**
 * Callback that enables an authentication module to inform the runtime of the groups a user is in.
 * The callback handler populates the given Subject with the group principals, or returns the
 * group names that were provided.
 */
public class GroupPrincipalCallback implements Callback {

    private final Subject subject;
    private final String[] groups;

    /**
     * Constructs a callback with a Subject and an array of group names. The callback handler
     * will create principals for the group names and add them to the subject.
     *
     * @param subject the subject to which the group principals will be added
     * @param groups  the group names associated with the caller
     */
    public GroupPrincipalCallback(Subject subject, String[] groups) {
        this.subject = subject;
        this.groups = groups;
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
     * Returns the group names associated with this callback.
     *
     * @return the group names
     */
    public String[] getGroups() {
        return groups;
    }
}
