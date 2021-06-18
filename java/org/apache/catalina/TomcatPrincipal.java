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
import java.util.Enumeration;

import org.ietf.jgss.GSSCredential;

/**
 * Defines additional methods implemented by {@link Principal}s created by
 * Tomcat's standard {@link Realm} implementations.
 */
public interface TomcatPrincipal extends Principal {

    /**
     * @return The authenticated Principal to be exposed to applications.
     */
    Principal getUserPrincipal();

    /**
     * @return The user's delegated credentials.
     */
    GSSCredential getGssCredential();

    /**
     * Calls logout, if necessary, on any associated JAASLoginContext and/or
     * GSSContext. May in the future be extended to cover other logout
     * requirements.
     *
     * @throws Exception If something goes wrong with the logout. Uses Exception
     *                   to allow for future expansion of this method to cover
     *                   other logout mechanisms that might throw a different
     *                   exception to LoginContext
     */
    void logout() throws Exception;

    /**
     * Returns the value of the named attribute as an <code>Object</code>, or
     * <code>null</code> if no attribute of the given name exists. May also
     * return <code>null</code>, if the named attribute exists but cannot be
     * returned in a way that ensures that changes made to the returned object
     * are not reflected by objects returned by subsequent calls to this method.
     * <p>
     * Only the servlet container may set attributes to make available custom
     * information about a Principal or the user it represents. For example,
     * some of the Realm implementations can be configured to additionally query
     * user attributes from the <i>user database</i>, which then are provided
     * through the Principal's attributes map.
     * <p>
     * In order to keep the attributes map <i>immutable</i>, the objects
     * returned by this method should always be <i>defensive copies</i> of the
     * objects contained in the attributes map. Any changes applied to these
     * objects must not be reflected by objects returned by subsequent calls to
     * this method. If that cannot be guaranteed (e. g. there is no way to copy
     * the object), the object's string representation (or even
     * <code>null</code>) shall be returned.
     * <p>
     * Attribute names and naming conventions are maintained by the Tomcat
     * components that contribute to this map, like some of the Realm
     * implementations.
     *
     * @param name a <code>String</code> specifying the name of the attribute
     * @return an <code>Object</code> containing the value of the attribute, or
     *         <code>null</code> if the attribute does not exist, or the
     *         object's string representation or <code>null</code> if its value
     *         cannot be copied in order to keep the attributes immutable
     */
    Object getAttribute(String name);

    /**
     * Returns an <code>Enumeration</code> containing the names of the
     * attributes available to this Principal. This method returns an empty
     * <code>Enumeration</code> if the Principal has no attributes available to
     * it.
     *
     * @return an <code>Enumeration</code> of strings containing the names of
     *         the Principal's attributes
     */
    Enumeration<String> getAttributeNames();

    /**
     * Determines whether attribute names are case-insensitive. May be
     * <code>true</code> if using <em>JNDIRealm</em> and then, depends on the
     * configured directory server.
     * 
     * @return <code>true</code>, if attribute names are case-insensitive;
     *         <code>false</code> otherwise
     */
    boolean isAttributesCaseIgnored();
}
