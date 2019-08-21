/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina;

import java.security.Principal;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSName;

/**
 * A <b>GSSRealm</b> is a specialized realm for GSS-based principals.
 *
 * @deprecated This will be removed in Tomcat 9 and integrated into {@link Realm}.
 */
@Deprecated
public interface GSSRealm extends Realm {


    // --------------------------------------------------------- Public Methods

    /**
     * Try to authenticate using a {@link GSSName}
     *
     * @param gssName The {@link GSSName} of the principal to look up
     * @param gssCredential The {@link GSSCredential} of the principal, may be
     *                      {@code null}
     * @return the associated principal, or {@code null} if there is none
     */
    public Principal authenticate(GSSName gssName, GSSCredential gssCredential);

}
