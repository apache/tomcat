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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.ietf.jgss.GSSContext;

/**
 * A <b>Realm</b> is a read-only facade for an underlying security realm
 * used to authenticate individual users, and identify the security roles
 * associated with those users.  Realms can be attached at any Container
 * level, but will typically only be attached to a Context, or higher level,
 * Container.
 *
 * @author Craig R. McClanahan
 */
public interface Realm extends Contained {

    /**
     * @return the CredentialHandler configured for this Realm.
     */
    public CredentialHandler getCredentialHandler();


    /**
     * Set the CredentialHandler to be used by this Realm.
     *
     * @param credentialHandler the {@link CredentialHandler} to use
     */
    public void setCredentialHandler(CredentialHandler credentialHandler);


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * Try to authenticate with the specified username.
     *
     * @param username Username of the Principal to look up
     * @return the associated principal, or <code>null</code> if none is
     *         associated.
     */
    public Principal authenticate(String username);


    /**
     * Try to authenticate using the specified username and
     * credentials.
     *
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     * authenticating this username
     * @return the associated principal, or <code>null</code> if there is none
     */
    public Principal authenticate(String username, String credentials);


    /**
     * Try to authenticate with the specified username, which
     * matches the digest calculated using the given parameters using the
     * method described in RFC 2617 (which is a superset of RFC 2069).
     *
     * @param username Username of the Principal to look up
     * @param digest Digest which has been submitted by the client
     * @param nonce Unique (or supposedly unique) token which has been used
     * for this request
     * @param nc the nonce counter
     * @param cnonce the client chosen nonce
     * @param qop the "quality of protection" (<code>nc</code> and <code>cnonce</code>
     *        will only be used, if <code>qop</code> is not <code>null</code>).
     * @param realm Realm name
     * @param md5a2 Second MD5 digest used to calculate the digest :
     * MD5(Method + ":" + uri)
     * @return the associated principal, or <code>null</code> if there is none.
     */
    public Principal authenticate(String username, String digest,
                                  String nonce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2);


    /**
     * Try to authenticate using a {@link GSSContext}
     *
     * @param gssContext The gssContext processed by the {@link Authenticator}.
     * @param storeCreds Should the realm attempt to store the delegated
     *                   credentials in the returned Principal?
     * @return the associated principal, or <code>null</code> if there is none
     */
    public Principal authenticate(GSSContext gssContext, boolean storeCreds);


    /**
     * Try to authenticate using {@link X509Certificate}s
     *
     * @param certs Array of client certificates, with the first one in
     *  the array being the certificate of the client itself.
     * @return the associated principal, or <code>null</code> if there is none
     */
    public Principal authenticate(X509Certificate certs[]);


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    public void backgroundProcess();


    /**
     * Find the SecurityConstraints configured to guard the request URI for
     * this request.
     *
     * @param request Request we are processing
     * @param context {@link Context} for this request
     * @return the configured {@link SecurityConstraint}, of <code>null</code>
     *         if there is none
     */
    public SecurityConstraint [] findSecurityConstraints(Request request,
                                                     Context context);


    /**
     * Perform access control based on the specified authorization constraint.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraint Security constraint we are enforcing
     * @param context The Context to which client of this class is attached.
     * @return <code>true</code> if this constraint is satisfied and processing
     *         should continue, or <code>false</code> otherwise
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean hasResourcePermission(Request request,
                                         Response response,
                                         SecurityConstraint [] constraint,
                                         Context context)
        throws IOException;


    /**
     * Check if the specified Principal has the specified
     * security role, within the context of this Realm.
     *
     * @param wrapper wrapper context for evaluating role
     * @param principal Principal for whom the role is to be checked
     * @param role Security role to be checked
     * @return <code>true</code> if the specified Principal has the specified
     *         security role, within the context of this Realm; otherwise return
     *         <code>false</code>.
     */
    public boolean hasRole(Wrapper wrapper, Principal principal, String role);


    /**
     * Enforce any user data constraint required by the security constraint
     * guarding this request URI.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraint Security constraint being checked
     * @return <code>true</code> if this constraint
     *         was not violated and processing should continue, or <code>false</code>
     *         if we have created a response already.
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean hasUserDataPermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraint)
        throws IOException;


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * Return roles associated with given principal
     * @param principal the {@link Principal} to get the roles for.
     * @return principal roles
     * @deprecated This will be removed in Tomcat 10.
     */
    @Deprecated
    public String[] getRoles(Principal principal);


    /**
     * Return the availability of the realm for authentication.
     * @return <code>true</code> if the realm is able to perform authentication
     */
    public boolean isAvailable();

}
