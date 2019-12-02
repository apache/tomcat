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
package org.apache.catalina.realm;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.annotation.ServletSecurity.TransportGuarantee;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.SessionConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * Simple implementation of <b>Realm</b> that reads an XML file to configure
 * the valid users, passwords, and roles.  The file format (and default file
 * location) are identical to those currently supported by Tomcat 3.X.
 *
 * @author Craig R. McClanahan
 */
@SuppressWarnings("deprecation")
public abstract class RealmBase extends LifecycleMBeanBase implements org.apache.catalina.GSSRealm {

    private static final Log log = LogFactory.getLog(RealmBase.class);

    private static final List<Class<? extends DigestCredentialHandlerBase>> credentialHandlerClasses =
            new ArrayList<>();

    static {
        // Order is important since it determines the search order for a
        // matching handler if only an algorithm is specified when calling
        // main()
        credentialHandlerClasses.add(MessageDigestCredentialHandler.class);
        credentialHandlerClasses.add(SecretKeyCredentialHandler.class);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * The Container with which this Realm is associated.
     */
    protected Container container = null;


    /**
     * Container log
     */
    protected Log containerLog = null;


    private CredentialHandler credentialHandler;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(RealmBase.class);


    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * Should we validate client certificate chains when they are presented?
     */
    protected boolean validate = true;

    /**
     * The name of the class to use for retrieving user names from X509
     * certificates.
     */
    protected String x509UsernameRetrieverClassName;

    /**
     * The object that will extract user names from X509 client certificates.
     */
    protected X509UsernameRetriever x509UsernameRetriever;

    /**
     * The all role mode.
     */
    protected AllRolesMode allRolesMode = AllRolesMode.STRICT_MODE;


    /**
     * When processing users authenticated via the GSS-API, should any
     * &quot;@...&quot; be stripped from the end of the user name?
     */
    protected boolean stripRealmForGss = true;


    private int transportGuaranteeRedirectStatus = HttpServletResponse.SC_FOUND;


    // ------------------------------------------------------------- Properties


    /**
     * @return The HTTP status code used when the container needs to issue an
     *         HTTP redirect to meet the requirements of a configured transport
     *         guarantee.
     */
    public int getTransportGuaranteeRedirectStatus() {
        return transportGuaranteeRedirectStatus;
    }


    /**
     * Set the HTTP status code used when the container needs to issue an HTTP
     * redirect to meet the requirements of a configured transport guarantee.
     *
     * @param transportGuaranteeRedirectStatus The status to use. This value is
     *                                         not validated
     */
    public void setTransportGuaranteeRedirectStatus(int transportGuaranteeRedirectStatus) {
        this.transportGuaranteeRedirectStatus = transportGuaranteeRedirectStatus;
    }


    @Override
    public CredentialHandler getCredentialHandler() {
        return credentialHandler;
    }


    @Override
    public void setCredentialHandler(CredentialHandler credentialHandler) {
        this.credentialHandler = credentialHandler;
    }


    /**
     * Return the Container with which this Realm has been associated.
     */
    @Override
    public Container getContainer() {
        return container;
    }


    /**
     * Set the Container with which this Realm has been associated.
     *
     * @param container The associated Container
     */
    @Override
    public void setContainer(Container container) {

        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);

    }

    /**
     * Return the all roles mode.
     * @return A string representation of the current all roles mode
     */
    public String getAllRolesMode() {
        return allRolesMode.toString();
    }


    /**
     * Set the all roles mode.
     * @param allRolesMode A string representation of the new all roles mode
     */
    public void setAllRolesMode(String allRolesMode) {
        this.allRolesMode = AllRolesMode.toMode(allRolesMode);
    }


    /**
     * Return the "validate certificate chains" flag.
     * @return The value of the validate certificate chains flag
     */
    public boolean getValidate() {
        return validate;
    }


    /**
     * Set the "validate certificate chains" flag.
     *
     * @param validate The new validate certificate chains flag
     */
    public void setValidate(boolean validate) {

        this.validate = validate;

    }

    /**
     * Gets the name of the class that will be used to extract user names
     * from X509 client certificates.
     * @return The name of the class that will be used to extract user names
     *         from X509 client certificates.
     */
    public String getX509UsernameRetrieverClassName() {
        return x509UsernameRetrieverClassName;
    }

    /**
     * Sets the name of the class that will be used to extract user names
     * from X509 client certificates. The class must implement
     * X509UsernameRetriever.
     *
     * @param className The name of the class that will be used to extract user names
     *                  from X509 client certificates.
     * @see X509UsernameRetriever
     */
    public void setX509UsernameRetrieverClassName(String className) {
        this.x509UsernameRetrieverClassName = className;
    }

    public boolean isStripRealmForGss() {
        return stripRealmForGss;
    }


    public void setStripRealmForGss(boolean stripRealmForGss) {
        this.stripRealmForGss = stripRealmForGss;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Return the Principal associated with the specified username, if there
     * is one; otherwise return <code>null</code>.
     *
     * @param username Username of the Principal to look up
     */
    @Override
    public Principal authenticate(String username) {

        if (username == null) {
            return null;
        }

        if (containerLog.isTraceEnabled()) {
            containerLog.trace(sm.getString("realmBase.authenticateSuccess", username));
        }

        return getPrincipal(username);
    }


    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     * @return the associated principal, or <code>null</code> if there is none.
     */
    @Override
    public Principal authenticate(String username, String credentials) {
        // No user or no credentials
        // Can't possibly authenticate, don't bother doing anything.
        if(username == null || credentials == null) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }

        // Look up the user's credentials
        String serverCredentials = getPassword(username);

        if (serverCredentials == null) {
            // User was not found
            // Waste a bit of time as not to reveal that the user does not exist.
            getCredentialHandler().mutate(credentials);

            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }

        boolean validated = getCredentialHandler().matches(credentials, serverCredentials);

        if (validated) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateSuccess",
                                                username));
            }
            return getPrincipal(username);
        } else {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }
    }

    /**
     * Try to authenticate with the specified username, which
     * matches the digest calculated using the given parameters using the
     * method described in RFC 2617 (which is a superset of RFC 2069).
     *
     * @param username Username of the Principal to look up
     * @param clientDigest Digest which has been submitted by the client
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
    @Override
    public Principal authenticate(String username, String clientDigest,
                                  String nonce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2) {

        // In digest auth, digests are always lower case
        String md5a1 = getDigest(username, realm);
        if (md5a1 == null)
            return null;
        md5a1 = md5a1.toLowerCase(Locale.ENGLISH);
        String serverDigestValue;
        if (qop == null) {
            serverDigestValue = md5a1 + ":" + nonce + ":" + md5a2;
        } else {
            serverDigestValue = md5a1 + ":" + nonce + ":" + nc + ":" +
                    cnonce + ":" + qop + ":" + md5a2;
        }

        byte[] valueBytes = null;
        try {
            valueBytes = serverDigestValue.getBytes(getDigestCharset());
        } catch (UnsupportedEncodingException uee) {
            log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
            throw new IllegalArgumentException(uee.getMessage());
        }

        String serverDigest = MD5Encoder.encode(ConcurrentMessageDigest.digestMD5(valueBytes));

        if (log.isDebugEnabled()) {
            log.debug("Digest : " + clientDigest + " Username:" + username
                    + " ClientDigest:" + clientDigest + " nonce:" + nonce
                    + " nc:" + nc + " cnonce:" + cnonce + " qop:" + qop
                    + " realm:" + realm + "md5a2:" + md5a2
                    + " Server digest:" + serverDigest);
        }

        if (serverDigest.equals(clientDigest)) {
            return getPrincipal(username);
        }

        return null;
    }


    /**
     * Return the Principal associated with the specified chain of X509
     * client certificates.  If there is none, return <code>null</code>.
     *
     * @param certs Array of client certificates, with the first one in
     *  the array being the certificate of the client itself.
     */
    @Override
    public Principal authenticate(X509Certificate certs[]) {

        if ((certs == null) || (certs.length < 1))
            return null;

        // Check the validity of each certificate in the chain
        if (log.isDebugEnabled())
            log.debug("Authenticating client certificate chain");
        if (validate) {
            for (int i = 0; i < certs.length; i++) {
                if (log.isDebugEnabled())
                    log.debug(" Checking validity for '" +
                        certs[i].getSubjectDN().getName() + "'");
                try {
                    certs[i].checkValidity();
                } catch (Exception e) {
                    if (log.isDebugEnabled())
                        log.debug("  Validity exception", e);
                    return null;
                }
            }
        }

        // Check the existence of the client Principal in our database
        return getPrincipal(certs[0]);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCred) {
        if (gssContext.isEstablished()) {
            GSSName gssName = null;
            try {
                gssName = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
            }

            if (gssName!= null) {
                GSSCredential gssCredential = null;
                if (storeCred) {
                    if (gssContext.getCredDelegState()) {
                        try {
                            gssCredential = gssContext.getDelegCred();
                        } catch (GSSException e) {
                            log.warn(sm.getString(
                                    "realmBase.delegatedCredentialFail", gssName), e);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString(
                                    "realmBase.credentialNotDelegated", gssName));
                        }
                    }
                }

                return getPrincipal(gssName, gssCredential);
            }
        } else {
            log.error(sm.getString("realmBase.gssContextNotEstablished"));
        }

        // Fail in all other cases
        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Principal authenticate(GSSName gssName, GSSCredential gssCredential) {
        if (gssName == null) {
            return null;
        }

        return getPrincipal(gssName, gssCredential);
    }


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public void backgroundProcess() {
        // NOOP in base class
    }


    /**
     * Return the SecurityConstraints configured to guard the request URI for
     * this request, or <code>null</code> if there is no such constraint.
     *
     * @param request Request we are processing
     * @param context Context the Request is mapped to
     */
    @Override
    public SecurityConstraint [] findSecurityConstraints(Request request,
                                                         Context context) {

        ArrayList<SecurityConstraint> results = null;
        // Are there any defined security constraints?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0)) {
            if (log.isDebugEnabled())
                log.debug("  No applicable constraints defined");
            return null;
        }

        // Check each defined security constraint
        String uri = request.getRequestPathMB().toString();
        // Bug47080 - in rare cases this may be null or ""
        // Mapper treats as '/' do the same to prevent NPE
        if (uri == null || uri.length() == 0) {
            uri = "/";
        }

        String method = request.getMethod();
        int i;
        boolean found = false;
        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                for(int k=0; k < patterns.length; k++) {
                    // Exact match including special case for the context root.
                    if(uri.equals(patterns[k]) || patterns[k].length() == 0 && uri.equals("/")) {
                        found = true;
                        if(collection[j].findMethod(method)) {
                            if(results == null) {
                                results = new ArrayList<>();
                            }
                            results.add(constraints[i]);
                        }
                    }
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        int longest = -1;

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                boolean matched = false;
                int length = -1;
                for(int k=0; k < patterns.length; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("/") && pattern.endsWith("/*") &&
                       pattern.length() >= longest) {

                        if(pattern.length() == 2) {
                            matched = true;
                            length = pattern.length();
                        } else if(pattern.regionMatches(0,uri,0,
                                                        pattern.length()-1) ||
                                  (pattern.length()-2 == uri.length() &&
                                   pattern.regionMatches(0,uri,0,
                                                        pattern.length()-2))) {
                            matched = true;
                            length = pattern.length();
                        }
                    }
                }
                if(matched) {
                    if(length > longest) {
                        found = false;
                        if(results != null) {
                            results.clear();
                        }
                        longest = length;
                    }
                    if(collection[j].findMethod(method)) {
                        found = true;
                        if(results == null) {
                            results = new ArrayList<>();
                        }
                        results.add(constraints[i]);
                    }
                }
            }
        }

        if(found) {
            return  resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            boolean matched = false;
            int pos = -1;
            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("*.")){
                        int slash = uri.lastIndexOf('/');
                        int dot = uri.lastIndexOf('.');
                        if(slash >= 0 && dot > slash &&
                           dot != uri.length()-1 &&
                           uri.length()-dot == pattern.length()-1) {
                            if(pattern.regionMatches(1,uri,dot,uri.length()-dot)) {
                                matched = true;
                                pos = j;
                            }
                        }
                    }
                }
            }
            if(matched) {
                found = true;
                if(collection[pos].findMethod(method)) {
                    if(results == null) {
                        results = new ArrayList<>();
                    }
                    results.add(constraints[i]);
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                boolean matched = false;
                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.equals("/")){
                        matched = true;
                    }
                }
                if(matched) {
                    if(results == null) {
                        results = new ArrayList<>();
                    }
                    results.add(constraints[i]);
                }
            }
        }

        if(results == null) {
            // No applicable security constraint was found
            if (log.isDebugEnabled())
                log.debug("  No applicable constraint located");
        }
        return resultsToArray(results);
    }

    /**
     * Convert an ArrayList to a SecurityConstraint [].
     */
    private SecurityConstraint [] resultsToArray(
            ArrayList<SecurityConstraint> results) {
        if(results == null || results.size() == 0) {
            return null;
        }
        SecurityConstraint [] array = new SecurityConstraint[results.size()];
        results.toArray(array);
        return array;
    }


    /**
     * Perform access control based on the specified authorization constraint.
     * Return <code>true</code> if this constraint is satisfied and processing
     * should continue, or <code>false</code> otherwise.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints Security constraint we are enforcing
     * @param context The Context to which client of this class is attached.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public boolean hasResourcePermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints,
                                         Context context)
        throws IOException {

        if (constraints == null || constraints.length == 0)
            return true;

        // Which user principal have we already authenticated?
        Principal principal = request.getPrincipal();
        boolean status = false;
        boolean denyfromall = false;
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];

            String roles[];
            if (constraint.getAllRoles()) {
                // * means all roles defined in web.xml
                roles = request.getContext().findSecurityRoles();
            } else {
                roles = constraint.findAuthRoles();
            }

            if (roles == null)
                roles = new String[0];

            if (log.isDebugEnabled())
                log.debug("  Checking roles " + principal);

            if (constraint.getAuthenticatedUsers() && principal != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Passing all authenticated users");
                }
                status = true;
            } else if (roles.length == 0 && !constraint.getAllRoles() &&
                    !constraint.getAuthenticatedUsers()) {
                if(constraint.getAuthConstraint()) {
                    if( log.isDebugEnabled() )
                        log.debug("No roles");
                    status = false; // No listed roles means no access at all
                    denyfromall = true;
                    break;
                }

                if(log.isDebugEnabled())
                    log.debug("Passing all access");
                status = true;
            } else if (principal == null) {
                if (log.isDebugEnabled())
                    log.debug("  No user authenticated, cannot grant access");
            } else {
                for (int j = 0; j < roles.length; j++) {
                    if (hasRole(request.getWrapper(), principal, roles[j])) {
                        status = true;
                        if( log.isDebugEnabled() )
                            log.debug( "Role found:  " + roles[j]);
                    }
                    else if( log.isDebugEnabled() )
                        log.debug( "No role found:  " + roles[j]);
                }
            }
        }

        if (!denyfromall && allRolesMode != AllRolesMode.STRICT_MODE &&
                !status && principal != null) {
            if (log.isDebugEnabled()) {
                log.debug("Checking for all roles mode: " + allRolesMode);
            }
            // Check for an all roles(role-name="*")
            for (int i = 0; i < constraints.length; i++) {
                SecurityConstraint constraint = constraints[i];
                String roles[];
                // If the all roles mode exists, sets
                if (constraint.getAllRoles()) {
                    if (allRolesMode == AllRolesMode.AUTH_ONLY_MODE) {
                        if (log.isDebugEnabled()) {
                            log.debug("Granting access for role-name=*, auth-only");
                        }
                        status = true;
                        break;
                    }

                    // For AllRolesMode.STRICT_AUTH_ONLY_MODE there must be zero roles
                    roles = request.getContext().findSecurityRoles();
                    if (roles.length == 0 && allRolesMode == AllRolesMode.STRICT_AUTH_ONLY_MODE) {
                        if (log.isDebugEnabled()) {
                            log.debug("Granting access for role-name=*, strict auth-only");
                        }
                        status = true;
                        break;
                    }
                }
            }
        }

        // Return a "Forbidden" message denying access to this resource
        if(!status) {
            response.sendError
                (HttpServletResponse.SC_FORBIDDEN,
                 sm.getString("realmBase.forbidden"));
        }
        return status;

    }


    /**
     * {@inheritDoc}
     *
     * This method or {@link #hasRoleInternal(Principal,
     * String)} can be overridden by Realm implementations, but the default is
     * adequate when an instance of <code>GenericPrincipal</code> is used to
     * represent authenticated Principals from this Realm.
     */
    @Override
    public boolean hasRole(Wrapper wrapper, Principal principal, String role) {
        // Check for a role alias
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if (realRole != null) {
                role = realRole;
            }
        }

        // Should be overridden in JAASRealm - to avoid pretty inefficient conversions
        if (principal == null || role == null) {
            return false;
        }

        boolean result = hasRoleInternal(principal, role);

        if (log.isDebugEnabled()) {
            String name = principal.getName();
            if (result)
                log.debug(sm.getString("realmBase.hasRoleSuccess", name, role));
            else
                log.debug(sm.getString("realmBase.hasRoleFailure", name, role));
        }

        return result;
    }


    /**
     * Check if the specified Principal has the specified
     * security role, within the context of this Realm.
     *
     * This method or {@link #hasRoleInternal(Principal,
     * String)} can be overridden by Realm implementations, but the default is
     * adequate when an instance of <code>GenericPrincipal</code> is used to
     * represent authenticated Principals from this Realm.
     *
     * @param principal Principal for whom the role is to be checked
     * @param role Security role to be checked
     *
     * @return <code>true</code> if the specified Principal has the specified
     *         security role, within the context of this Realm; otherwise return
     *         <code>false</code>.
     */
    protected boolean hasRoleInternal(Principal principal, String role) {
        // Should be overridden in JAASRealm - to avoid pretty inefficient conversions
        if (!(principal instanceof GenericPrincipal)) {
            return false;
        }

        GenericPrincipal gp = (GenericPrincipal) principal;
        return gp.hasRole(role);
    }


    /**
     * Enforce any user data constraint required by the security constraint
     * guarding this request URI.  Return <code>true</code> if this constraint
     * was not violated and processing should continue, or <code>false</code>
     * if we have created a response already.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints Security constraint being checked
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public boolean hasUserDataPermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints)
        throws IOException {

        // Is there a relevant user data constraint?
        if (constraints == null || constraints.length == 0) {
            if (log.isDebugEnabled())
                log.debug("  No applicable security constraint defined");
            return true;
        }
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];
            String userConstraint = constraint.getUserConstraint();
            if (userConstraint == null) {
                if (log.isDebugEnabled())
                    log.debug("  No applicable user data constraint defined");
                return true;
            }
            if (userConstraint.equals(TransportGuarantee.NONE.name())) {
                if (log.isDebugEnabled())
                    log.debug("  User data constraint has no restrictions");
                return true;
            }

        }
        // Validate the request against the user data constraint
        if (request.getRequest().isSecure()) {
            if (log.isDebugEnabled())
                log.debug("  User data constraint already satisfied");
            return true;
        }
        // Initialize variables we need to determine the appropriate action
        int redirectPort = request.getConnector().getRedirectPort();

        // Is redirecting disabled?
        if (redirectPort <= 0) {
            if (log.isDebugEnabled())
                log.debug("  SSL redirect is disabled");
            response.sendError
                (HttpServletResponse.SC_FORBIDDEN,
                 request.getRequestURI());
            return false;
        }

        // Redirect to the corresponding SSL port
        StringBuilder file = new StringBuilder();
        String protocol = "https";
        String host = request.getServerName();
        // Protocol
        file.append(protocol).append("://").append(host);
        // Host with port
        if(redirectPort != 443) {
            file.append(":").append(redirectPort);
        }
        // URI
        file.append(request.getRequestURI());
        String requestedSessionId = request.getRequestedSessionId();
        if ((requestedSessionId != null) &&
            request.isRequestedSessionIdFromURL()) {
            file.append(";");
            file.append(SessionConfig.getSessionUriParamName(
                    request.getContext()));
            file.append("=");
            file.append(requestedSessionId);
        }
        String queryString = request.getQueryString();
        if (queryString != null) {
            file.append('?');
            file.append(queryString);
        }
        if (log.isDebugEnabled())
            log.debug("  Redirecting to " + file.toString());
        response.sendRedirect(file.toString(), transportGuaranteeRedirectStatus);
        return false;

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        // We want logger as soon as possible
        if (container != null) {
            this.containerLog = container.getLogger();
        }

        x509UsernameRetriever = createUsernameRetriever(x509UsernameRetrieverClassName);
    }

    /**
     * Prepare for the beginning of active use of the public methods of this
     * component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {
        if (credentialHandler == null) {
            credentialHandler = new MessageDigestCredentialHandler();
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Realm[");
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    // ------------------------------------------------------ Protected Methods

    protected boolean hasMessageDigest() {
        CredentialHandler ch = credentialHandler;
        if (ch instanceof MessageDigestCredentialHandler) {
            return ((MessageDigestCredentialHandler) ch).getAlgorithm() != null;
        }
        return false;
    }


    /**
     * Return the digest associated with given principal's user name.
     * @param username the user name
     * @param realmName the realm name
     * @return the digest for the specified user
     */
    protected String getDigest(String username, String realmName) {
        if (hasMessageDigest()) {
            // Use pre-generated digest
            return getPassword(username);
        }

        String digestValue = username + ":" + realmName + ":"
            + getPassword(username);

        byte[] valueBytes = null;
        try {
            valueBytes = digestValue.getBytes(getDigestCharset());
        } catch (UnsupportedEncodingException uee) {
            log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
            throw new IllegalArgumentException(uee.getMessage());
        }

        return MD5Encoder.encode(ConcurrentMessageDigest.digestMD5(valueBytes));
    }


    private String getDigestEncoding() {
        CredentialHandler ch = credentialHandler;
        if (ch instanceof MessageDigestCredentialHandler) {
            return ((MessageDigestCredentialHandler) ch).getEncoding();
        }
        return null;
    }


    private Charset getDigestCharset() throws UnsupportedEncodingException {
        String charset = getDigestEncoding();
        if (charset == null) {
            return StandardCharsets.ISO_8859_1;
        } else {
            return B2CConverter.getCharset(charset);
        }
    }


    /**
     * @return a short name for this Realm implementation, for use in
     * log messages.
     *
     * @deprecated This will be removed in Tomcat 9 onwards. Use
     *             {@link Class#getSimpleName()} instead.
     */
    @Deprecated
    protected abstract String getName();


    /**
     * Get the password for the specified user.
     * @param username The user name
     * @return the password associated with the given principal's user name.
     */
    protected abstract String getPassword(String username);


    /**
     * Get the principal associated with the specified certificate.
     * @param usercert The user certificate
     * @return the Principal associated with the given certificate.
     */
    protected Principal getPrincipal(X509Certificate usercert) {
        String username = x509UsernameRetriever.getUsername(usercert);

        if(log.isDebugEnabled())
            log.debug(sm.getString("realmBase.gotX509Username", username));

        return(getPrincipal(username));
    }


    /**
     * Get the principal associated with the specified user.
     * @param username The user name
     * @return the Principal associated with the given user name.
     */
    protected abstract Principal getPrincipal(String username);


    /**
     * @deprecated This will be removed in Tomcat 10. Use
     *             {@link #getPrincipal(GSSName, GSSCredential)} instead.
     */
    @Deprecated
    protected Principal getPrincipal(String username,
            GSSCredential gssCredential) {
        Principal p = getPrincipal(username);

        if (p instanceof GenericPrincipal) {
            ((GenericPrincipal) p).setGssCredential(gssCredential);
        }

        return p;
    }


    /**
     * Get the principal associated with the specified {@link GSSName}.
     *
     * @param gssName The GSS name
     * @param gssCredential the GSS credential of the principal
     * @return the principal associated with the given user name.
     */
    protected Principal getPrincipal(GSSName gssName, GSSCredential gssCredential) {
        String name = gssName.toString();

        if (isStripRealmForGss()) {
            int i = name.indexOf('@');
            if (i > 0) {
                // Zero so we don't leave a zero length name
                name = name.substring(0, i);
            }
        }

        Principal p = getPrincipal(name);

        if (p instanceof GenericPrincipal) {
            ((GenericPrincipal) p).setGssCredential(gssCredential);
        }

        return p;
    }


    /**
     * Return the Server object that is the ultimate parent for the container
     * with which this Realm is associated. If the server cannot be found (eg
     * because the container hierarchy is not complete), <code>null</code> is
     * returned.
     * @return the Server associated with the realm
     */
    protected Server getServer() {
        Container c = container;
        if (c instanceof Context) {
            c = c.getParent();
        }
        if (c instanceof Host) {
            c = c.getParent();
        }
        if (c instanceof Engine) {
            Service s = ((Engine)c).getService();
            if (s != null) {
                return s.getServer();
            }
        }
        return null;
    }


    // --------------------------------------------------------- Static Methods

    /**
     * Digest password using the algorithm specified and convert the result to a
     * corresponding hex string.
     *
     * @param credentials Password or other credentials to use in authenticating
     *                    this username
     * @param algorithm   Algorithm used to do the digest
     * @param encoding    Character encoding of the string to digest
     *
     * @return The digested credentials as a hex string or the original plain
     *         text credentials if an error occurs.
     *
     * @deprecated  Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    public static final String Digest(String credentials, String algorithm,
                                      String encoding) {

        try {
            // Obtain a new message digest with "digest" encryption
            MessageDigest md =
                (MessageDigest) MessageDigest.getInstance(algorithm).clone();

            // encode the credentials
            // Should use the digestEncoding, but that's not a static field
            if (encoding == null) {
                md.update(credentials.getBytes());
            } else {
                md.update(credentials.getBytes(encoding));
            }

            // Digest the credentials and return as hexadecimal
            return (HexUtils.toHexString(md.digest()));
        } catch(Exception ex) {
            log.error(ex);
            return credentials;
        }

    }


    /**
     * Generate a stored credential string for the given password and associated
     * parameters.
     * <p>The following parameters are supported:</p>
     * <ul>
     * <li><b>-a</b> - The algorithm to use to generate the stored
     *                 credential. If not specified a default of SHA-512 will be
     *                 used.</li>
     * <li><b>-e</b> - The encoding to use for any byte to/from character
     *                 conversion that may be necessary. If not specified, the
     *                 system encoding ({@link Charset#defaultCharset()}) will
     *                 be used.</li>
     * <li><b>-i</b> - The number of iterations to use when generating the
     *                 stored credential. If not specified, the default for the
     *                 CredentialHandler will be used.</li>
     * <li><b>-s</b> - The length (in bytes) of salt to generate and store as
     *                 part of the credential. If not specified, the default for
     *                 the CredentialHandler will be used.</li>
     * <li><b>-k</b> - The length (in bits) of the key(s), if any, created while
     *                 generating the credential. If not specified, the default
     *                 for the CredentialHandler will be used.</li>
     * <li><b>-h</b> - The fully qualified class name of the CredentialHandler
     *                 to use. If not specified, the built-in handlers will be
     *                 tested in turn and the first one to accept the specified
     *                 algorithm will be used.</li>
     * </ul>
     * <p>This generation process currently supports the following
     * CredentialHandlers, the correct one being selected based on the algorithm
     * specified:</p>
     * <ul>
     * <li>{@link MessageDigestCredentialHandler}</li>
     * <li>{@link SecretKeyCredentialHandler}</li>
     * </ul>
     * @param args The parameters passed on the command line
     */
    public static void main(String args[]) {

        // Use negative values since null is not an option to indicate 'not set'
        int saltLength = -1;
        int iterations = -1;
        int keyLength = -1;
        // Default
        String encoding = Charset.defaultCharset().name();
        // Default values for these depend on whether either of them are set on
        // the command line
        String algorithm = null;
        String handlerClassName = null;

        if (args.length == 0) {
            usage();
            return;
        }

        int argIndex = 0;

        while (args.length > argIndex + 2 && args[argIndex].length() == 2 &&
                args[argIndex].charAt(0) == '-' ) {
            switch (args[argIndex].charAt(1)) {
            case 'a': {
                algorithm = args[argIndex + 1];
                break;
            }
            case 'e': {
                encoding = args[argIndex + 1];
                break;
            }
            case 'i': {
                iterations = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 's': {
                saltLength = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 'k': {
                keyLength = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 'h': {
                handlerClassName = args[argIndex + 1];
                break;
            }
            default: {
                usage();
                return;
            }
            }
            argIndex += 2;
        }

        // Determine defaults for -a and -h. The rules are more complex to
        // express than the implementation:
        // - if neither -a nor -h is set, use SHA-512 and
        //   MessageDigestCredentialHandler
        // - if only -a is set the built-in handlers will be searched in order
        //   (MessageDigestCredentialHandler, SecretKeyCredentialHandler) and
        //   the first handler that supports the algorithm will be used
        // - if only -h is set no default will be used for -a. The handler may
        //   or may nor support -a and may or may not supply a sensible default
        if (algorithm == null && handlerClassName == null) {
            algorithm = "SHA-512";
        }

        CredentialHandler handler = null;

        if (handlerClassName == null) {
            for (Class<? extends DigestCredentialHandlerBase> clazz : credentialHandlerClasses) {
                try {
                    handler = clazz.getConstructor().newInstance();
                    if (IntrospectionUtils.setProperty(handler, "algorithm", algorithm)) {
                        break;
                    }
                } catch (ReflectiveOperationException e) {
                    // This isn't good.
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                Class<?> clazz = Class.forName(handlerClassName);
                handler = (DigestCredentialHandlerBase) clazz.getConstructor().newInstance();
                IntrospectionUtils.setProperty(handler, "algorithm", algorithm);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        if (handler == null) {
            throw new RuntimeException(new NoSuchAlgorithmException(algorithm));
        }

        IntrospectionUtils.setProperty(handler, "encoding", encoding);
        if (iterations > 0) {
            IntrospectionUtils.setProperty(handler, "iterations", Integer.toString(iterations));
        }
        if (saltLength > -1) {
            IntrospectionUtils.setProperty(handler, "saltLength", Integer.toString(saltLength));
        }
        if (keyLength > 0) {
            IntrospectionUtils.setProperty(handler, "keyLength", Integer.toString(keyLength));
        }

        for (; argIndex < args.length; argIndex++) {
            String credential = args[argIndex];
            System.out.print(credential + ":");
            System.out.println(handler.mutate(credential));
        }
    }


    private static void usage() {
        System.out.println("Usage: RealmBase [-a <algorithm>] [-e <encoding>] " +
                "[-i <iterations>] [-s <salt-length>] [-k <key-length>] " +
                "[-h <handler-class-name>] <credentials>");
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    public String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Realm");
        keyProperties.append(getRealmSuffix());
        keyProperties.append(container.getMBeanKeyProperties());

        return keyProperties.toString();
    }

    @Override
    public String getDomainInternal() {
        return container.getDomain();
    }

    protected String realmPath = "/realm0";

    public String getRealmPath() {
        return realmPath;
    }

    public void setRealmPath(String theRealmPath) {
        realmPath = theRealmPath;
    }

    protected String getRealmSuffix() {
        return ",realmPath=" + getRealmPath();
    }


    protected static class AllRolesMode {

        private final String name;
        /** Use the strict servlet spec interpretation which requires that the user
         * have one of the web-app/security-role/role-name
         */
        public static final AllRolesMode STRICT_MODE = new AllRolesMode("strict");
        /** Allow any authenticated user
         */
        public static final AllRolesMode AUTH_ONLY_MODE = new AllRolesMode("authOnly");
        /** Allow any authenticated user only if there are no web-app/security-roles
         */
        public static final AllRolesMode STRICT_AUTH_ONLY_MODE = new AllRolesMode("strictAuthOnly");

        static AllRolesMode toMode(String name)
        {
            AllRolesMode mode;
            if( name.equalsIgnoreCase(STRICT_MODE.name) )
                mode = STRICT_MODE;
            else if( name.equalsIgnoreCase(AUTH_ONLY_MODE.name) )
                mode = AUTH_ONLY_MODE;
            else if( name.equalsIgnoreCase(STRICT_AUTH_ONLY_MODE.name) )
                mode = STRICT_AUTH_ONLY_MODE;
            else
                throw new IllegalStateException("Unknown mode, must be one of: strict, authOnly, strictAuthOnly");
            return mode;
        }

        private AllRolesMode(String name)
        {
            this.name = name;
        }

        @Override
        public boolean equals(Object o)
        {
            boolean equals = false;
            if( o instanceof AllRolesMode )
            {
                AllRolesMode mode = (AllRolesMode) o;
                equals = name.equals(mode.name);
            }
            return equals;
        }
        @Override
        public int hashCode()
        {
            return name.hashCode();
        }
        @Override
        public String toString()
        {
            return name;
        }
    }

    private static X509UsernameRetriever createUsernameRetriever(String className)
        throws LifecycleException {
        if(null == className || "".equals(className.trim()))
            return new X509SubjectDnRetriever();

        try {
            @SuppressWarnings("unchecked")
            Class<? extends X509UsernameRetriever> clazz = (Class<? extends X509UsernameRetriever>)Class.forName(className);
            return clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new LifecycleException(sm.getString("realmBase.createUsernameRetriever.newInstance", className), e);
        } catch (ClassCastException e) {
            throw new LifecycleException(sm.getString("realmBase.createUsernameRetriever.ClassCastException", className), e);
        }
    }


    @Override
    public String[] getRoles(Principal principal) {
        if (principal instanceof GenericPrincipal) {
            return ((GenericPrincipal) principal).getRoles();
        }

        String className = principal.getClass().getSimpleName();
        throw new IllegalStateException(sm.getString("realmBase.cannotGetRoles", className));
    }
}
