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
package jakarta.security.auth.message;

import javax.security.auth.Subject;

public interface ServerAuth {

    /**
     * Validate the request.
     *
     * @param messageInfo       The associated request and response
     * @param clientSubject     The subject that represents the source of the
     *                          request
     * @param serviceSubject    The subject that represents the recipient of the
     *                          request
     *
     * @return An AuthStatus instance that represents the result of the
     *         validation
     *
     * @throws AuthException If the a failure occurred in a manner that
     *                       prevented the failure from being communicated via
     *                       messageInfo
     */
    AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException;

    /**
     * Secure (authenticate) the response.
     *
     * @param messageInfo       The associated request and response
     * @param serviceSubject    The subject that represents the source of the
     *                          response
     *
     * @return An AuthStatus instance that represents the result of the
     *         authentication
     *
     * @throws AuthException If the a failure occurred in a manner that
     *                       prevented the failure from being communicated via
     *                       messageInfo
     */
    default AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) throws AuthException {
        return AuthStatus.SUCCESS;
    }

    /**
     * Remove principals and/or credentials from the subject that were
     * previously added by this authentication mechanism.
     *
     * @param messageInfo   The associated request and response
     * @param subject       The subject to clean
     *
     * @throws AuthException If the a failure occurred
     */
    default void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
        // NO-OP
    }
}
