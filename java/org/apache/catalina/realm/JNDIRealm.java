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

import java.io.IOException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NameParser;
import javax.naming.Name;
import javax.naming.AuthenticationException;
import javax.naming.PartialResultException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.util.Base64;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * <p>Implementation of <strong>Realm</strong> that works with a directory
 * server accessed via the Java Naming and Directory Interface (JNDI) APIs.
 * The following constraints are imposed on the data structure in the
 * underlying directory server:</p>
 * <ul>
 *
 * <li>Each user that can be authenticated is represented by an individual
 *     element in the top level <code>DirContext</code> that is accessed
 *     via the <code>connectionURL</code> property.</li>
 *
 * <li>If a socket connection can not be made to the <code>connectURL</code>
 *     an attempt will be made to use the <code>alternateURL</code> if it
 *     exists.</li>
 *
 * <li>Each user element has a distinguished name that can be formed by
 *     substituting the presented username into a pattern configured by the
 *     <code>userPattern</code> property.</li>
 *
 * <li>Alternatively, if the <code>userPattern</code> property is not
 *     specified, a unique element can be located by searching the directory
 *     context. In this case:
 *     <ul>
 *     <li>The <code>userSearch</code> pattern specifies the search filter
 *         after substitution of the username.</li>
 *     <li>The <code>userBase</code> property can be set to the element that
 *         is the base of the subtree containing users.  If not specified,
 *         the search base is the top-level context.</li>
 *     <li>The <code>userSubtree</code> property can be set to
 *         <code>true</code> if you wish to search the entire subtree of the
 *         directory context.  The default value of <code>false</code>
 *         requests a search of only the current level.</li>
 *    </ul>
 * </li>
 *
 * <li>The user may be authenticated by binding to the directory with the
 *      username and password presented. This method is used when the
 *      <code>userPassword</code> property is not specified.</li>
 *
 * <li>The user may be authenticated by retrieving the value of an attribute
 *     from the directory and comparing it explicitly with the value presented
 *     by the user. This method is used when the <code>userPassword</code>
 *     property is specified, in which case:
 *     <ul>
 *     <li>The element for this user must contain an attribute named by the
 *         <code>userPassword</code> property.
 *     <li>The value of the user password attribute is either a cleartext
 *         String, or the result of passing a cleartext String through the
 *         <code>RealmBase.digest()</code> method (using the standard digest
 *         support included in <code>RealmBase</code>).
 *     <li>The user is considered to be authenticated if the presented
 *         credentials (after being passed through
 *         <code>RealmBase.digest()</code>) are equal to the retrieved value
 *         for the user password attribute.</li>
 *     </ul></li>
 *
 * <li>Each group of users that has been assigned a particular role may be
 *     represented by an individual element in the top level
 *     <code>DirContext</code> that is accessed via the
 *     <code>connectionURL</code> property.  This element has the following
 *     characteristics:
 *     <ul>
 *     <li>The set of all possible groups of interest can be selected by a
 *         search pattern configured by the <code>roleSearch</code>
 *         property.</li>
 *     <li>The <code>roleSearch</code> pattern optionally includes pattern
 *         replacements "{0}" for the distinguished name, and/or "{1}" for
 *         the username, of the authenticated user for which roles will be
 *         retrieved.</li>
 *     <li>The <code>roleBase</code> property can be set to the element that
 *         is the base of the search for matching roles.  If not specified,
 *         the entire context will be searched.</li>
 *     <li>The <code>roleSubtree</code> property can be set to
 *         <code>true</code> if you wish to search the entire subtree of the
 *         directory context.  The default value of <code>false</code>
 *         requests a search of only the current level.</li>
 *     <li>The element includes an attribute (whose name is configured by
 *         the <code>roleName</code> property) containing the name of the
 *         role represented by this element.</li>
 *     </ul></li>
 *
 * <li>In addition, roles may be represented by the values of an attribute
 * in the user's element whose name is configured by the
 * <code>userRoleName</code> property.</li>
 *
 * <li>A default role can be assigned to each user that was successfully
 * authenticated by setting the <code>commonRole</code> property to the
 * name of this role. The role doesn't have to exist in the directory.</li>
 *
 * <li>If the directory server contains nested roles, you can search for roles
 * recursively by setting <code>roleRecursionLimit</code> to some positive value.
 * The default value is <code>0</code>, so role searches do not recurse.</li>
 *
 * <li>Note that the standard <code>&lt;security-role-ref&gt;</code> element in
 *     the web application deployment descriptor allows applications to refer
 *     to roles programmatically by names other than those used in the
 *     directory server itself.</li>
 * </ul>
 *
 * <p><strong>TODO</strong> - Support connection pooling (including message
 * format objects) so that <code>authenticate()</code> does not have to be
 * synchronized.</p>
 *
 * <p><strong>WARNING</strong> - There is a reported bug against the Netscape
 * provider code (com.netscape.jndi.ldap.LdapContextFactory) with respect to
 * successfully authenticated a non-existing user. The
 * report is here: http://issues.apache.org/bugzilla/show_bug.cgi?id=11210 .
 * With luck, Netscape has updated their provider code and this is not an
 * issue. </p>
 *
 * @author John Holman
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public class JNDIRealm extends RealmBase {


    // ----------------------------------------------------- Instance Variables

    /**
     *  The type of authentication to use
     */
    protected String authentication = null;

    /**
     * The connection username for the server we will contact.
     */
    protected String connectionName = null;


    /**
     * The connection password for the server we will contact.
     */
    protected String connectionPassword = null;


    /**
     * The connection URL for the server we will contact.
     */
    protected String connectionURL = null;


    /**
     * The directory context linking us to our directory server.
     */
    protected DirContext context = null;


    /**
     * The JNDI context factory used to acquire our InitialContext.  By
     * default, assumes use of an LDAP server using the standard JNDI LDAP
     * provider.
     */
    protected String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";


    /**
     * How aliases should be dereferenced during search operations.
     */
    protected String derefAliases = null;

    /**
     * Constant that holds the name of the environment property for specifying
     * the manner in which aliases should be dereferenced.
     */
    public final static String DEREF_ALIASES = "java.naming.ldap.derefAliases";

    /**
     * Descriptive information about this Realm implementation.
     */
    protected static final String info =
        "org.apache.catalina.realm.JNDIRealm/1.0";


    /**
     * Descriptive information about this Realm implementation.
     */
    protected static final String name = "JNDIRealm";


    /**
     * The protocol that will be used in the communication with the
     * directory server.
     */
    protected String protocol = null;


    /**
     * Should we ignore PartialResultExceptions when iterating over NamingEnumerations?
     * Microsoft Active Directory often returns referrals, which lead
     * to PartialResultExceptions. Unfortunately there's no stable way to detect,
     * if the Exceptions really come from an AD referral.
     * Set to true to ignore PartialResultExceptions.
     */
    protected boolean adCompat = false;


    /**
     * How should we handle referrals?  Microsoft Active Directory often returns
     * referrals. If you need to follow them set referrals to "follow".
     * Caution: if your DNS is not part of AD, the LDAP client lib might try
     * to resolve your domain name in DNS to find another LDAP server.
     */
    protected String referrals = null;


    /**
     * The base element for user searches.
     */
    protected String userBase = "";


    /**
     * The message format used to search for a user, with "{0}" marking
     * the spot where the username goes.
     */
    protected String userSearch = null;


    /**
     * The MessageFormat object associated with the current
     * <code>userSearch</code>.
     */
    protected MessageFormat userSearchFormat = null;


    /**
     * Should we search the entire subtree for matching users?
     */
    protected boolean userSubtree = false;


    /**
     * The attribute name used to retrieve the user password.
     */
    protected String userPassword = null;


    /**
     * A string of LDAP user patterns or paths, ":"-separated
     * These will be used to form the distinguished name of a
     * user, with "{0}" marking the spot where the specified username
     * goes.
     * This is similar to userPattern, but allows for multiple searches
     * for a user.
     */
    protected String[] userPatternArray = null;


    /**
     * The message format used to form the distinguished name of a
     * user, with "{0}" marking the spot where the specified username
     * goes.
     */
    protected String userPattern = null;


    /**
     * An array of MessageFormat objects associated with the current
     * <code>userPatternArray</code>.
     */
    protected MessageFormat[] userPatternFormatArray = null;


    /**
     * The maximum recursion depth when resolving roles recursively.
     * By default we don't resolve roles recursively.
     */
    protected int roleRecursionLimit = 0;


    /**
     * The base element for role searches.
     */
    protected String roleBase = "";


    /**
     * The MessageFormat object associated with the current
     * <code>roleSearch</code>.
     */
    protected MessageFormat roleFormat = null;


    /**
     * The name of an attribute in the user's entry containing
     * roles for that user
     */
    protected String userRoleName = null;


    /**
     * The name of the attribute containing roles held elsewhere
     */
    protected String roleName = null;


    /**
     * The message format used to select roles for a user, with "{0}" marking
     * the spot where the distinguished name of the user goes.
     */
    protected String roleSearch = null;


    /**
     * Should we search the entire subtree for matching memberships?
     */
    protected boolean roleSubtree = false;

    /**
     * An alternate URL, to which, we should connect if connectionURL fails.
     */
    protected String alternateURL;

    /**
     * The number of connection attempts.  If greater than zero we use the
     * alternate url.
     */
    protected int connectionAttempt = 0;

    /**
     *  Add this role to every authenticated user
     */
    protected String commonRole = null;


    // ------------------------------------------------------------- Properties

    /**
     * Return the type of authentication to use.
     */
    public String getAuthentication() {

        return authentication;

    }

    /**
     * Set the type of authentication to use.
     *
     * @param authentication The authentication
     */
    public void setAuthentication(String authentication) {

        this.authentication = authentication;

    }

    /**
     * Return the connection username for this Realm.
     */
    public String getConnectionName() {

        return (this.connectionName);

    }


    /**
     * Set the connection username for this Realm.
     *
     * @param connectionName The new connection username
     */
    public void setConnectionName(String connectionName) {

        this.connectionName = connectionName;

    }


    /**
     * Return the connection password for this Realm.
     */
    public String getConnectionPassword() {

        return (this.connectionPassword);

    }


    /**
     * Set the connection password for this Realm.
     *
     * @param connectionPassword The new connection password
     */
    public void setConnectionPassword(String connectionPassword) {

        this.connectionPassword = connectionPassword;

    }


    /**
     * Return the connection URL for this Realm.
     */
    public String getConnectionURL() {

        return (this.connectionURL);

    }


    /**
     * Set the connection URL for this Realm.
     *
     * @param connectionURL The new connection URL
     */
    public void setConnectionURL(String connectionURL) {

        this.connectionURL = connectionURL;

    }


    /**
     * Return the JNDI context factory for this Realm.
     */
    public String getContextFactory() {

        return (this.contextFactory);

    }


    /**
     * Set the JNDI context factory for this Realm.
     *
     * @param contextFactory The new context factory
     */
    public void setContextFactory(String contextFactory) {

        this.contextFactory = contextFactory;

    }

    /**
     * Return the derefAliases setting to be used.
     */
    public java.lang.String getDerefAliases() {
        return derefAliases;
    }

    /**
     * Set the value for derefAliases to be used when searching the directory.
     *
     * @param derefAliases New value of property derefAliases.
     */
    public void setDerefAliases(java.lang.String derefAliases) {
      this.derefAliases = derefAliases;
    }

    /**
     * Return the protocol to be used.
     */
    public String getProtocol() {

        return protocol;

    }

    /**
     * Set the protocol for this Realm.
     *
     * @param protocol The new protocol.
     */
    public void setProtocol(String protocol) {

        this.protocol = protocol;

    }


    /**
     * Returns the current settings for handling PartialResultExceptions
     */
    public boolean getAdCompat () {
        return adCompat;
    }


    /**
     * How do we handle PartialResultExceptions?
     * True: ignore all PartialResultExceptions.
     */
    public void setAdCompat (boolean adCompat) {
        this.adCompat = adCompat;
    }


    /**
     * Returns the current settings for handling JNDI referrals.
     */
    public String getReferrals () {
        return referrals;
    }


    /**
     * How do we handle JNDI referrals? ignore, follow, or throw
     * (see javax.naming.Context.REFERRAL for more information).
     */
    public void setReferrals (String referrals) {
        this.referrals = referrals;
    }


    /**
     * Return the base element for user searches.
     */
    public String getUserBase() {

        return (this.userBase);

    }


    /**
     * Set the base element for user searches.
     *
     * @param userBase The new base element
     */
    public void setUserBase(String userBase) {

        this.userBase = userBase;

    }


    /**
     * Return the message format pattern for selecting users in this Realm.
     */
    public String getUserSearch() {

        return (this.userSearch);

    }


    /**
     * Set the message format pattern for selecting users in this Realm.
     *
     * @param userSearch The new user search pattern
     */
    public void setUserSearch(String userSearch) {

        this.userSearch = userSearch;
        if (userSearch == null)
            userSearchFormat = null;
        else
            userSearchFormat = new MessageFormat(userSearch);

    }


    /**
     * Return the "search subtree for users" flag.
     */
    public boolean getUserSubtree() {

        return (this.userSubtree);

    }


    /**
     * Set the "search subtree for users" flag.
     *
     * @param userSubtree The new search flag
     */
    public void setUserSubtree(boolean userSubtree) {

        this.userSubtree = userSubtree;

    }


    /**
     * Return the user role name attribute name for this Realm.
     */
    public String getUserRoleName() {

        return userRoleName;
    }


    /**
     * Set the user role name attribute name for this Realm.
     *
     * @param userRoleName The new userRole name attribute name
     */
    public void setUserRoleName(String userRoleName) {

        this.userRoleName = userRoleName;

    }


    /**
     * Return the maximum recursion depth for role searches.
     */
    public int getRoleRecursionLimit() {

        return (this.roleRecursionLimit);

    }


    /**
     * Set the maximum recursion depth for role searches.
     *
     * @param roleRecursionLimit The new recursion limit
     */
    public void setRoleRecursionLimit(int roleRecursionLimit) {

        this.roleRecursionLimit = roleRecursionLimit;

    }


    /**
     * Return the base element for role searches.
     */
    public String getRoleBase() {

        return (this.roleBase);

    }


    /**
     * Set the base element for role searches.
     *
     * @param roleBase The new base element
     */
    public void setRoleBase(String roleBase) {

        this.roleBase = roleBase;

    }


    /**
     * Return the role name attribute name for this Realm.
     */
    public String getRoleName() {

        return (this.roleName);

    }


    /**
     * Set the role name attribute name for this Realm.
     *
     * @param roleName The new role name attribute name
     */
    public void setRoleName(String roleName) {

        this.roleName = roleName;

    }


    /**
     * Return the message format pattern for selecting roles in this Realm.
     */
    public String getRoleSearch() {

        return (this.roleSearch);

    }


    /**
     * Set the message format pattern for selecting roles in this Realm.
     *
     * @param roleSearch The new role search pattern
     */
    public void setRoleSearch(String roleSearch) {

        this.roleSearch = roleSearch;
        if (roleSearch == null)
            roleFormat = null;
        else
            roleFormat = new MessageFormat(roleSearch);

    }


    /**
     * Return the "search subtree for roles" flag.
     */
    public boolean getRoleSubtree() {

        return (this.roleSubtree);

    }


    /**
     * Set the "search subtree for roles" flag.
     *
     * @param roleSubtree The new search flag
     */
    public void setRoleSubtree(boolean roleSubtree) {

        this.roleSubtree = roleSubtree;

    }


    /**
     * Return the password attribute used to retrieve the user password.
     */
    public String getUserPassword() {

        return (this.userPassword);

    }


    /**
     * Set the password attribute used to retrieve the user password.
     *
     * @param userPassword The new password attribute
     */
    public void setUserPassword(String userPassword) {

        this.userPassword = userPassword;

    }


    /**
     * Return the message format pattern for selecting users in this Realm.
     */
    public String getUserPattern() {

        return (this.userPattern);

    }


    /**
     * Set the message format pattern for selecting users in this Realm.
     * This may be one simple pattern, or multiple patterns to be tried,
     * separated by parentheses. (for example, either "cn={0}", or
     * "(cn={0})(cn={0},o=myorg)" Full LDAP search strings are also supported,
     * but only the "OR", "|" syntax, so "(|(cn={0})(cn={0},o=myorg))" is
     * also valid. Complex search strings with &, etc are NOT supported.
     *
     * @param userPattern The new user pattern
     */
    public void setUserPattern(String userPattern) {

        this.userPattern = userPattern;
        if (userPattern == null)
            userPatternArray = null;
        else {
            userPatternArray = parseUserPatternString(userPattern);
            int len = this.userPatternArray.length;
            userPatternFormatArray = new MessageFormat[len];
            for (int i=0; i < len; i++) {
                userPatternFormatArray[i] =
                    new MessageFormat(userPatternArray[i]);
            }
        }
    }


    /**
     * Getter for property alternateURL.
     *
     * @return Value of property alternateURL.
     */
    public String getAlternateURL() {

        return this.alternateURL;

    }


    /**
     * Setter for property alternateURL.
     *
     * @param alternateURL New value of property alternateURL.
     */
    public void setAlternateURL(String alternateURL) {

        this.alternateURL = alternateURL;

    }


    /**
     * Return the common role
     */
    public String getCommonRole() {

        return commonRole;

    }


    /**
     * Set the common role
     *
     * @param commonRole The common role
     */
    public void setCommonRole(String commonRole) {

        this.commonRole = commonRole;

    }


    // ---------------------------------------------------------- Realm Methods


    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * If there are any errors with the JDBC connection, executing
     * the query or anything we return null (don't authenticate). This
     * event is also logged, and the connection will be closed so that
     * a subsequent request will automatically re-open it.
     *
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     */
    public Principal authenticate(String username, String credentials) {

        DirContext context = null;
        Principal principal = null;

        try {

            // Ensure that we have a directory context available
            context = open();

            // Occassionally the directory context will timeout.  Try one more
            // time before giving up.
            try {

                // Authenticate the specified username if possible
                principal = authenticate(context, username, credentials);

            } catch (NullPointerException e) {
                /* BZ 42449 - Kludge Sun's LDAP provider
                   with broken SSL
                */
                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                principal = authenticate(context, username, credentials);

            } catch (CommunicationException e) {

                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                principal = authenticate(context, username, credentials);

            } catch (ServiceUnavailableException e) {

                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                principal = authenticate(context, username, credentials);

            }


            // Release this context
            release(context);

            // Return the authenticated Principal (if any)
            return (principal);

        } catch (NamingException e) {

            // Log the problem for posterity
            containerLog.error(sm.getString("jndiRealm.exception"), e);

            // Close the connection so that it gets reopened next time
            if (context != null)
                close(context);

            // Return "not authenticated" for this request
            if (containerLog.isDebugEnabled())
                containerLog.debug("Returning null principal.");
            return (null);

        }

    }


    // -------------------------------------------------------- Package Methods


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the Principal associated with the specified username and
     * credentials, if there is one; otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username Username of the Principal to look up
     * @param credentials Password or other credentials to use in
     *  authenticating this username
     *
     * @exception NamingException if a directory server error occurs
     */
    public synchronized Principal authenticate(DirContext context,
                                               String username,
                                               String credentials)
        throws NamingException {

        if (username == null || username.equals("")
            || credentials == null || credentials.equals("")) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("username null or empty: returning null principal.");
            return (null);
        }

        if (userPatternArray != null) {
            for (int curUserPattern = 0;
                 curUserPattern < userPatternFormatArray.length;
                 curUserPattern++) {
                // Retrieve user information
                User user = getUser(context, username, credentials, curUserPattern);
                if (user != null) {
                    try {
                        // Check the user's credentials
                        if (checkCredentials(context, user, credentials)) {
                            // Search for additional roles
                            List<String> roles = getRoles(context, user);
                            if (containerLog.isDebugEnabled()) {
                                Iterator<String> it = roles.iterator();
                                while (it.hasNext()) {
                                    containerLog.debug("Found role: " + it.next());
                                }
                            }
                            return (new GenericPrincipal(this,
                                                         username,
                                                         credentials,
                                                         roles));
                        }
                    } catch (InvalidNameException ine) {
                        // Log the problem for posterity
                        containerLog.warn(sm.getString("jndiRealm.exception"), ine);
                        // ignore; this is probably due to a name not fitting
                        // the search path format exactly, as in a fully-
                        // qualified name being munged into a search path
                        // that already contains cn= or vice-versa
                    }
                }
            }
            return null;
        } else {
            // Retrieve user information
            User user = getUser(context, username, credentials);
            if (user == null)
                return (null);

            // Check the user's credentials
            if (!checkCredentials(context, user, credentials))
                return (null);

            // Search for additional roles
            List<String> roles = getRoles(context, user);
            if (containerLog.isDebugEnabled()) {
                Iterator<String> it = roles.iterator();
                while (it.hasNext()) {
                    containerLog.debug("Found role: " + it.next());
                }
            }

            // Create and return a suitable Principal for this user
            return (new GenericPrincipal(this, username, credentials, roles));
        }
    }


    /**
     * Return a User object containing information about the user
     * with the specified username, if found in the directory;
     * otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username Username to be looked up
     *
     * @exception NamingException if a directory server error occurs
     *
     * @see #getUser(DirContext, String, String, int)
     */
    protected User getUser(DirContext context, String username)
        throws NamingException {

        return getUser(context, username, null, -1);
    }


    /**
     * Return a User object containing information about the user
     * with the specified username, if found in the directory;
     * otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username Username to be looked up
     * @param credentials User credentials (optional)
     *
     * @exception NamingException if a directory server error occurs
     *
     * @see #getUser(DirContext, String, int)
     */
    protected User getUser(DirContext context, String username, String credentials)
        throws NamingException {

        return getUser(context, username, credentials, -1);
    }


    /**
     * Return a User object containing information about the user
     * with the specified username, if found in the directory;
     * otherwise return <code>null</code>.
     *
     * If the <code>userPassword</code> configuration attribute is
     * specified, the value of that attribute is retrieved from the
     * user's directory entry. If the <code>userRoleName</code>
     * configuration attribute is specified, all values of that
     * attribute are retrieved from the directory entry.
     *
     * @param context The directory context
     * @param username Username to be looked up
     * @param credentials User credentials (optional)
     * @param curUserPattern Index into userPatternFormatArray
     *
     * @exception NamingException if a directory server error occurs
     */
    protected User getUser(DirContext context, String username,
                           String credentials, int curUserPattern)
        throws NamingException {

        User user = null;

        // Get attributes to retrieve from user entry
        ArrayList<String> list = new ArrayList<String>();
        if (userPassword != null)
            list.add(userPassword);
        if (userRoleName != null)
            list.add(userRoleName);
        String[] attrIds = new String[list.size()];
        list.toArray(attrIds);

        // Use pattern or search for user entry
        if (userPatternFormatArray != null && curUserPattern >= 0) {
            user = getUserByPattern(context, username, credentials, attrIds, curUserPattern);
        } else {
            user = getUserBySearch(context, username, attrIds);
        }

        return user;
    }


    /**
     * Use the distinguished name to locate the directory
     * entry for the user with the specified username and
     * return a User object; otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username The username
     * @param attrIds String[]containing names of attributes to
     * @param dn Distinguished name of the user
     * retrieve.
     *
     * @exception NamingException if a directory server error occurs
     */
    protected User getUserByPattern(DirContext context,
                                    String username,
                                    String[] attrIds,
                                    String dn)
        throws NamingException {

        // Get required attributes from user entry
        Attributes attrs = null;
        try {
            attrs = context.getAttributes(dn, attrIds);
        } catch (NameNotFoundException e) {
            return (null);
        }
        if (attrs == null)
            return (null);

        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null)
            password = getAttributeValue(userPassword, attrs);

        // Retrieve values of userRoleName attribute
        ArrayList<String> roles = null;
        if (userRoleName != null)
            roles = addAttributeValues(userRoleName, attrs, roles);

        return new User(username, dn, password, roles);
    }


    /**
     * Use the <code>UserPattern</code> configuration attribute to
     * locate the directory entry for the user with the specified
     * username and return a User object; otherwise return
     * <code>null</code>.
     *
     * @param context The directory context
     * @param username The username
     * @param credentials User credentials (optional)
     * @param attrIds String[]containing names of attributes to
     * @param curUserPattern Index into userPatternFormatArray
     *
     * @exception NamingException if a directory server error occurs
     * @see #getUserByPattern(DirContext, String, String[], String)
     */
    protected User getUserByPattern(DirContext context,
                                    String username,
                                    String credentials,
                                    String[] attrIds,
                                    int curUserPattern)
        throws NamingException {

        User user = null;

        if (username == null || userPatternFormatArray[curUserPattern] == null)
            return (null);

        // Form the dn from the user pattern
        String dn = userPatternFormatArray[curUserPattern].format(new String[] { username });

        try {
            user = getUserByPattern(context, username, attrIds, dn);
        } catch (NameNotFoundException e) {
            return (null);
        } catch (NamingException e) {
            // If the getUserByPattern() call fails, try it again with the
            // credentials of the user that we're searching for
            try {
                // Set up security environment to bind as the user
                context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
                context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);

                user = getUserByPattern(context, username, attrIds, dn);
            } finally {
                // Restore the original security environment
                if (connectionName != null) {
                    context.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                             connectionName);
                } else {
                    context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
                }

                if (connectionPassword != null) {
                    context.addToEnvironment(Context.SECURITY_CREDENTIALS,
                                             connectionPassword);
                }
                else {
                    context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
                }
            }
        }
        return user;
    }


    /**
     * Search the directory to return a User object containing
     * information about the user with the specified username, if
     * found in the directory; otherwise return <code>null</code>.
     *
     * @param context The directory context
     * @param username The username
     * @param attrIds String[]containing names of attributes to retrieve.
     *
     * @exception NamingException if a directory server error occurs
     */
    protected User getUserBySearch(DirContext context,
                                   String username,
                                   String[] attrIds)
        throws NamingException {

        if (username == null || userSearchFormat == null)
            return (null);

        // Form the search filter
        String filter = userSearchFormat.format(new String[] { username });

        // Set up the search controls
        SearchControls constraints = new SearchControls();

        if (userSubtree) {
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }
        else {
            constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        }

        // Specify the attributes to be retrieved
        if (attrIds == null)
            attrIds = new String[0];
        constraints.setReturningAttributes(attrIds);

        NamingEnumeration results =
            context.search(userBase, filter, constraints);


        // Fail if no entries found
        try {
            if (results == null || !results.hasMore()) {
                return (null);
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
            else
                return (null);
        }

        // Get result for the first entry found
        SearchResult result = (SearchResult)results.next();

        // Check no further entries were found
        try {
            if (results.hasMore()) {
                if(containerLog.isInfoEnabled())
                    containerLog.info("username " + username + " has multiple entries");
                return (null);
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
        }

        String dn = getDistinguishedName(context, userBase, result);

        if (containerLog.isTraceEnabled())
            containerLog.trace("  entry found for " + username + " with dn " + dn);

        // Get the entry's attributes
        Attributes attrs = result.getAttributes();
        if (attrs == null)
            return null;

        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null)
            password = getAttributeValue(userPassword, attrs);

        // Retrieve values of userRoleName attribute
        ArrayList<String> roles = null;
        if (userRoleName != null)
            roles = addAttributeValues(userRoleName, attrs, roles);

        return new User(username, dn, password, roles);
    }


    /**
     * Check whether the given User can be authenticated with the
     * given credentials. If the <code>userPassword</code>
     * configuration attribute is specified, the credentials
     * previously retrieved from the directory are compared explicitly
     * with those presented by the user. Otherwise the presented
     * credentials are checked by binding to the directory as the
     * user.
     *
     * @param context The directory context
     * @param user The User to be authenticated
     * @param credentials The credentials presented by the user
     *
     * @exception NamingException if a directory server error occurs
     */
    protected boolean checkCredentials(DirContext context,
                                     User user,
                                     String credentials)
         throws NamingException {

         boolean validated = false;

         if (userPassword == null) {
             validated = bindAsUser(context, user, credentials);
         } else {
             validated = compareCredentials(context, user, credentials);
         }

         if (containerLog.isTraceEnabled()) {
             if (validated) {
                 containerLog.trace(sm.getString("jndiRealm.authenticateSuccess",
                                  user.username));
             } else {
                 containerLog.trace(sm.getString("jndiRealm.authenticateFailure",
                                  user.username));
             }
         }
         return (validated);
     }



    /**
     * Check whether the credentials presented by the user match those
     * retrieved from the directory.
     *
     * @param context The directory context
     * @param info The User to be authenticated
     * @param credentials Authentication credentials
     *
     * @exception NamingException if a directory server error occurs
     */
    protected boolean compareCredentials(DirContext context,
                                         User info,
                                         String credentials)
        throws NamingException {

        if (info == null || credentials == null)
            return (false);

        String password = info.password;
        if (password == null)
            return (false);

        // Validate the credentials specified by the user
        if (containerLog.isTraceEnabled())
            containerLog.trace("  validating credentials");

        boolean validated = false;
        if (hasMessageDigest()) {
            // iPlanet support if the values starts with {SHA1}
            // The string is in a format compatible with Base64.encode not
            // the Hex encoding of the parent class.
            if (password.startsWith("{SHA}")) {
                /* sync since super.digest() does this same thing */
                synchronized (this) {
                    password = password.substring(5);
                    md.reset();
                    md.update(credentials.getBytes());
                    String digestedPassword =
                        new String(Base64.encode(md.digest()));
                    validated = password.equals(digestedPassword);
                }
            } else if (password.startsWith("{SSHA}")) {
                // Bugzilla 32938
                /* sync since super.digest() does this same thing */
                synchronized (this) {
                    password = password.substring(6);

                    md.reset();
                    md.update(credentials.getBytes());

                    // Decode stored password.
                    ByteChunk pwbc = new ByteChunk(password.length());
                    try {
                        pwbc.append(password.getBytes(), 0, password.length());
                    } catch (IOException e) {
                        // Should never happen
                        containerLog.error("Could not append password bytes to chunk: ", e);
                    }

                    CharChunk decoded = new CharChunk();
                    Base64.decode(pwbc, decoded);
                    char[] pwarray = decoded.getBuffer();

                    // Split decoded password into hash and salt.
                    final int saltpos = 20;
                    byte[] hash = new byte[saltpos];
                    for (int i=0; i< hash.length; i++) {
                        hash[i] = (byte) pwarray[i];
                    }

                    byte[] salt = new byte[pwarray.length - saltpos];
                    for (int i=0; i< salt.length; i++)
                        salt[i] = (byte)pwarray[i+saltpos];

                    md.update(salt);
                    byte[] dp = md.digest();

                    validated = Arrays.equals(dp, hash);
                } // End synchronized(this) block
            } else {
                // Hex hashes should be compared case-insensitive
                validated = (digest(credentials).equalsIgnoreCase(password));
            }
        } else
            validated = (digest(credentials).equals(password));
        return (validated);

    }



    /**
     * Check credentials by binding to the directory as the user
     *
     * @param context The directory context
     * @param user The User to be authenticated
     * @param credentials Authentication credentials
     *
     * @exception NamingException if a directory server error occurs
     */
     protected boolean bindAsUser(DirContext context,
                                  User user,
                                  String credentials)
         throws NamingException {

         if (credentials == null || user == null)
             return (false);

         String dn = user.dn;
         if (dn == null)
             return (false);

         // Validate the credentials specified by the user
         if (containerLog.isTraceEnabled()) {
             containerLog.trace("  validating credentials by binding as the user");
        }

        // Set up security environment to bind as the user
        context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
        context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);

        // Elicit an LDAP bind operation
        boolean validated = false;
        try {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  binding as "  + dn);
            }
            context.getAttributes("", null);
            validated = true;
        }
        catch (AuthenticationException e) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  bind attempt failed");
            }
        }

        // Restore the original security environment
        if (connectionName != null) {
            context.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                     connectionName);
        } else {
            context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
        }

        if (connectionPassword != null) {
            context.addToEnvironment(Context.SECURITY_CREDENTIALS,
                                     connectionPassword);
        }
        else {
            context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
        }

        return (validated);
     }


    /**
     * Add roles to a user and search for other roles containing them themselves.
     * We search recursively with a limited depth.
     * By default the depth is 0, and we only use direct roles.
     * The search needs to use the distinguished role names,
     * but to return the role names.
     *
     * @param depth Recursion depth, starting at zero
     * @param context The directory context we are searching
     * @param recursiveMap The cumulative result map of role names and DNs.
     * @param recursiveSet The cumulative result set of role names.
     * @param groupName The role name to add to the list.
     * @param groupDName The distinguished name of the role.
     *
     * @exception NamingException if a directory server error occurs
     */
    private void getRolesRecursive(int depth, DirContext context, Map<String, String> recursiveMap, Set<String> recursiveSet,
                                     String groupName, String groupDName) throws NamingException {
        if (containerLog.isTraceEnabled())
            containerLog.trace("Recursive search depth " + depth + " for group '" + groupDName + " (" + groupName + ")'");
        // Adding the given group to the result set if not already found
        if (!recursiveSet.contains(groupDName)) {
            recursiveSet.add(groupDName);
            recursiveMap.put(groupDName, groupName);
            if (depth >= roleRecursionLimit) {
                if (roleRecursionLimit > 0)
                    containerLog.warn("Terminating recursive role search because of recursion limit " +
                                      roleRecursionLimit + ", results might be incomplete");
                return;
            }
            // Prepare the parameters for searching groups
            String filter = roleFormat.format(new String[] { groupDName });
            SearchControls controls = new SearchControls();
            controls.setSearchScope(roleSubtree ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
            controls.setReturningAttributes(new String[] { roleName });
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("Recursive search in role base '" + roleBase + "' for attribute '" + roleName + "'" +
                                   " with filter expression '" + filter + "'");
            }
            // Searching groups that assign the given group
            NamingEnumeration results = context.search(roleBase, filter, controls);
            if (results != null) {
                // Iterate over the resulting groups
                try {
                    while (results.hasMore()) {
                        SearchResult result = (SearchResult) results.next();
                        Attributes attrs = result.getAttributes();
                        if (attrs == null)
                            continue;
                        String dname = getDistinguishedName(context, roleBase, result);
                        String name = getAttributeValue(roleName, attrs);
                        if (name != null && dname != null) {
                           getRolesRecursive(depth+1, context, recursiveMap, recursiveSet, name, dname);
                        }
                    }
                } catch (PartialResultException ex) {
                    if (!adCompat)
                        throw ex;
                }
            }
        }
    }

    /**
     * Return a List of roles associated with the given User.  Any
     * roles present in the user's directory entry are supplemented by
     * a directory search. If no roles are associated with this user,
     * a zero-length List is returned.
     *
     * @param context The directory context we are searching
     * @param user The User to be checked
     *
     * @exception NamingException if a directory server error occurs
     */
    protected List<String> getRoles(DirContext context, User user)
        throws NamingException {

        if (user == null)
            return (null);

        String dn = user.dn;
        String username = user.username;

        if (dn == null || username == null)
            return (null);

        if (containerLog.isTraceEnabled())
            containerLog.trace("  getRoles(" + dn + ")");

        // Start with roles retrieved from the user entry
        ArrayList<String> list = user.roles;
        if (list == null) {
            list = new ArrayList<String>();
        }
        if (commonRole != null)
            list.add(commonRole);

        if (containerLog.isTraceEnabled()) {
            if (list != null) {
                containerLog.trace("  Found " + list.size() + " user internal roles");
                for (int i=0; i<list.size(); i++)
                    containerLog.trace(  "  Found user internal role " + list.get(i));
            } else {
                containerLog.trace("  Found no user internal roles");
            }
        }

        // Are we configured to do role searches?
        if ((roleFormat == null) || (roleName == null))
            return (list);

        // Set up parameters for an appropriate search
        String filter = roleFormat.format(new String[] { doRFC2254Encoding(dn), username });
        SearchControls controls = new SearchControls();
        if (roleSubtree)
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        else
            controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        controls.setReturningAttributes(new String[] {roleName});

        // Perform the configured search and process the results
        NamingEnumeration results =
            context.search(roleBase, filter, controls);
        if (results == null)
            return (list);  // Should never happen, but just in case ...

        HashMap<String, String> groupMap = new HashMap<String, String>();
        try {
            while (results.hasMore()) {
                SearchResult result = (SearchResult) results.next();
                Attributes attrs = result.getAttributes();
                if (attrs == null)
                    continue;
                String dname = getDistinguishedName(context, roleBase, result);
                String name = getAttributeValue(roleName, attrs);
                if (name != null && dname != null) {
                    groupMap.put(dname, name);
                }
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
        }

        Set<String> keys = groupMap.keySet();
        if (containerLog.isTraceEnabled()) {
            containerLog.trace("  Found " + keys.size() + " direct roles");
            for (Iterator<String> i = keys.iterator(); i.hasNext();) {
                Object k = i.next();
                containerLog.trace(  "  Found direct role " + k + " -> " + groupMap.get(k));
            }
        }

        HashSet<String> recursiveSet = new HashSet<String>();
        HashMap<String, String> recursiveMap = new HashMap<String, String>();

        for (Iterator<String> i = keys.iterator(); i.hasNext();) {
            String k = i.next();
            getRolesRecursive(0, context, recursiveMap, recursiveSet, groupMap.get(k), k);
        }

        HashSet<String> resultSet = new HashSet<String>(list);
        resultSet.addAll(recursiveMap.values());

        if (containerLog.isTraceEnabled()) {
            containerLog.trace("  Returning " + resultSet.size() + " roles");
            for (Iterator<String> i = resultSet.iterator(); i.hasNext();)
                containerLog.trace(  "  Found role " + i.next());
        }

        return new ArrayList<String>(resultSet);
    }


    /**
     * Return a String representing the value of the specified attribute.
     *
     * @param attrId Attribute name
     * @param attrs Attributes containing the required value
     *
     * @exception NamingException if a directory server error occurs
     */
    private String getAttributeValue(String attrId, Attributes attrs)
        throws NamingException {

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving attribute " + attrId);

        if (attrId == null || attrs == null)
            return null;

        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return (null);
        Object value = attr.get();
        if (value == null)
            return (null);
        String valueString = null;
        if (value instanceof byte[])
            valueString = new String((byte[]) value);
        else
            valueString = value.toString();

        return valueString;
    }



    /**
     * Add values of a specified attribute to a list
     *
     * @param attrId Attribute name
     * @param attrs Attributes containing the new values
     * @param values ArrayList containing values found so far
     *
     * @exception NamingException if a directory server error occurs
     */
    private ArrayList<String> addAttributeValues(String attrId,
                                         Attributes attrs,
                                         ArrayList<String> values)
        throws NamingException{

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving values for attribute " + attrId);
        if (attrId == null || attrs == null)
            return values;
        if (values == null)
            values = new ArrayList<String>();
        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return (values);
        NamingEnumeration e = attr.getAll();
        try {
            while(e.hasMore()) {
                String value = (String)e.next();
                values.add(value);
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
        }
        return values;
    }


    /**
     * Close any open connection to the directory server for this Realm.
     *
     * @param context The directory context to be closed
     */
    protected void close(DirContext context) {

        // Do nothing if there is no opened connection
        if (context == null)
            return;

        // Close our opened connection
        try {
            if (containerLog.isDebugEnabled())
                containerLog.debug("Closing directory context");
            context.close();
        } catch (NamingException e) {
            containerLog.error(sm.getString("jndiRealm.close"), e);
        }
        this.context = null;

    }


    /**
     * Return a short name for this Realm implementation.
     */
    protected String getName() {

        return (name);

    }


    /**
     * Return the password associated with the given principal's user name.
     */
    protected String getPassword(String username) {

        return (null);

    }

    /**
     * Return the Principal associated with the given user name.
     */
    protected Principal getPrincipal(String username) {

        DirContext context = null;
        Principal principal = null;

        try {

            // Ensure that we have a directory context available
            context = open();

            // Occassionally the directory context will timeout.  Try one more
            // time before giving up.
            try {

                // Authenticate the specified username if possible
                principal = getPrincipal(context, username);

            } catch (CommunicationException e) {

                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                principal = getPrincipal(context, username);

            } catch (ServiceUnavailableException e) {

                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // close the connection so we know it will be reopened.
                if (context != null)
                    close(context);

                // open a new directory context.
                context = open();

                // Try the authentication again.
                principal = getPrincipal(context, username);

            }


            // Release this context
            release(context);

            // Return the authenticated Principal (if any)
            return (principal);

        } catch (NamingException e) {

            // Log the problem for posterity
            containerLog.error(sm.getString("jndiRealm.exception"), e);

            // Close the connection so that it gets reopened next time
            if (context != null)
                close(context);

            // Return "not authenticated" for this request
            return (null);

        }


    }


    /**
     * Return the Principal associated with the given user name.
     */
    protected synchronized Principal getPrincipal(DirContext context,
                                                  String username)
        throws NamingException {

        User user = getUser(context, username);

        return new GenericPrincipal(this, user.username, user.password ,
                getRoles(context, user));
    }

    /**
     * Open (if necessary) and return a connection to the configured
     * directory server for this Realm.
     *
     * @exception NamingException if a directory server error occurs
     */
    protected DirContext open() throws NamingException {

        // Do nothing if there is a directory server connection already open
        if (context != null)
            return (context);

        try {

            // Ensure that we have a directory context available
            context = new InitialDirContext(getDirectoryContextEnvironment());

        } catch (Exception e) {

            connectionAttempt = 1;

            // log the first exception.
            containerLog.warn(sm.getString("jndiRealm.exception"), e);

            // Try connecting to the alternate url.
            context = new InitialDirContext(getDirectoryContextEnvironment());

        } finally {

            // reset it in case the connection times out.
            // the primary may come back.
            connectionAttempt = 0;

        }

        return (context);

    }

    /**
     * Create our directory context configuration.
     *
     * @return java.util.Hashtable the configuration for the directory context.
     */
    protected Hashtable getDirectoryContextEnvironment() {

        Hashtable<String,String> env = new Hashtable<String,String>();

        // Configure our directory context environment.
        if (containerLog.isDebugEnabled() && connectionAttempt == 0)
            containerLog.debug("Connecting to URL " + connectionURL);
        else if (containerLog.isDebugEnabled() && connectionAttempt > 0)
            containerLog.debug("Connecting to URL " + alternateURL);
        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        if (connectionName != null)
            env.put(Context.SECURITY_PRINCIPAL, connectionName);
        if (connectionPassword != null)
            env.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        if (connectionURL != null && connectionAttempt == 0)
            env.put(Context.PROVIDER_URL, connectionURL);
        else if (alternateURL != null && connectionAttempt > 0)
            env.put(Context.PROVIDER_URL, alternateURL);
        if (authentication != null)
            env.put(Context.SECURITY_AUTHENTICATION, authentication);
        if (protocol != null)
            env.put(Context.SECURITY_PROTOCOL, protocol);
        if (referrals != null)
            env.put(Context.REFERRAL, referrals);
        if (derefAliases != null)
            env.put(JNDIRealm.DEREF_ALIASES, derefAliases);

        return env;

    }


    /**
     * Release our use of this connection so that it can be recycled.
     *
     * @param context The directory context to release
     */
    protected void release(DirContext context) {

        ; // NO-OP since we are not pooling anything

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Prepare for active use of the public methods of this Component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public void start() throws LifecycleException {

        // Perform normal superclass initialization
        super.start();

        // Validate that we can open our connection
        try {
            open();
        } catch (NamingException e) {
            throw new LifecycleException(sm.getString("jndiRealm.open"), e);
        }

    }


    /**
     * Gracefully shut down active use of the public methods of this Component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Perform normal superclass finalization
        super.stop();

        // Close any open directory server connection
        close(this.context);

    }

    /**
     * Given a string containing LDAP patterns for user locations (separated by
     * parentheses in a pseudo-LDAP search string format -
     * "(location1)(location2)", returns an array of those paths.  Real LDAP
     * search strings are supported as well (though only the "|" "OR" type).
     *
     * @param userPatternString - a string LDAP search paths surrounded by
     * parentheses
     */
    protected String[] parseUserPatternString(String userPatternString) {

        if (userPatternString != null) {
            ArrayList<String> pathList = new ArrayList<String>();
            int startParenLoc = userPatternString.indexOf('(');
            if (startParenLoc == -1) {
                // no parens here; return whole thing
                return new String[] {userPatternString};
            }
            int startingPoint = 0;
            while (startParenLoc > -1) {
                int endParenLoc = 0;
                // weed out escaped open parens and parens enclosing the
                // whole statement (in the case of valid LDAP search
                // strings: (|(something)(somethingelse))
                while ( (userPatternString.charAt(startParenLoc + 1) == '|') ||
                        (startParenLoc != 0 && userPatternString.charAt(startParenLoc - 1) == '\\') ) {
                    startParenLoc = userPatternString.indexOf("(", startParenLoc+1);
                }
                endParenLoc = userPatternString.indexOf(")", startParenLoc+1);
                // weed out escaped end-parens
                while (userPatternString.charAt(endParenLoc - 1) == '\\') {
                    endParenLoc = userPatternString.indexOf(")", endParenLoc+1);
                }
                String nextPathPart = userPatternString.substring
                    (startParenLoc+1, endParenLoc);
                pathList.add(nextPathPart);
                startingPoint = endParenLoc+1;
                startParenLoc = userPatternString.indexOf('(', startingPoint);
            }
            return (String[])pathList.toArray(new String[] {});
        }
        return null;

    }


    /**
     * Given an LDAP search string, returns the string with certain characters
     * escaped according to RFC 2254 guidelines.
     * The character mapping is as follows:
     *     char ->  Replacement
     *    ---------------------------
     *     *  -> \2a
     *     (  -> \28
     *     )  -> \29
     *     \  -> \5c
     *     \0 -> \00
     * @param inString string to escape according to RFC 2254 guidelines
     * @return String the escaped/encoded result
     */
    protected String doRFC2254Encoding(String inString) {
        StringBuffer buf = new StringBuffer(inString.length());
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            switch (c) {
                case '\\':
                    buf.append("\\5c");
                    break;
                case '*':
                    buf.append("\\2a");
                    break;
                case '(':
                    buf.append("\\28");
                    break;
                case ')':
                    buf.append("\\29");
                    break;
                case '\0':
                    buf.append("\\00");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }


    /**
     * Returns the distinguished name of a search result.
     *
     * @param context Our DirContext
     * @param base The base DN
     * @param result The search result
     * @return String containing the distinguished name
     */
    protected String getDistinguishedName(DirContext context, String base, SearchResult result)
        throws NamingException {
        // Get the entry's distinguished name
        NameParser parser = context.getNameParser("");
        Name contextName = parser.parse(context.getNameInNamespace());
        Name baseName = parser.parse(base);

        // Bugzilla 32269
        Name entryName = parser.parse(new CompositeName(result.getName()).get(0));

        Name name = contextName.addAll(baseName);
        name = name.addAll(entryName);
        return name.toString();
    }


}

// ------------------------------------------------------ Private Classes

/**
 * A private class representing a User
 */
class User {
    String username = null;
    String dn = null;
    String password = null;
    ArrayList<String> roles = null;


    User(String username, String dn, String password,
            ArrayList<String> roles) {
        this.username = username;
        this.dn = dn;
        this.password = password;
        this.roles = roles;
    }

}
