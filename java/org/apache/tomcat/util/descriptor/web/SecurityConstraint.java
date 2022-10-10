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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.HttpConstraintElement;
import jakarta.servlet.HttpMethodConstraintElement;
import jakarta.servlet.ServletSecurityElement;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.annotation.ServletSecurity.EmptyRoleSemantic;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;


/**
 * Representation of a security constraint element for a web application,
 * as represented in a <code>&lt;security-constraint&gt;</code> element in the
 * deployment descriptor.
 * <p>
 * <b>WARNING</b>:  It is assumed that instances of this class will be created
 * and modified only within the context of a single thread, before the instance
 * is made visible to the remainder of the application.  After that, only read
 * access is expected.  Therefore, none of the read and write access within
 * this class is synchronized.
 *
 * @author Craig R. McClanahan
 */
public class SecurityConstraint extends XmlEncodingBase implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_ALL_ROLES = "*";
    public static final String ROLE_ALL_AUTHENTICATED_USERS = "**";

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new security constraint instance with default values.
     */
    public SecurityConstraint() {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Was the "all roles" wildcard - {@link #ROLE_ALL_ROLES} - included in the
     * authorization constraints for this security constraint?
     */
    private boolean allRoles = false;


    /**
     * Was the "all authenticated users" wildcard -
     * {@link #ROLE_ALL_AUTHENTICATED_USERS} - included in the authorization
     * constraints for this security constraint?
     */
    private boolean authenticatedUsers = false;


    /**
     * Was an authorization constraint included in this security constraint?
     * This is necessary to distinguish the case where an auth-constraint with
     * no roles (signifying no direct access at all) was requested, versus
     * a lack of auth-constraint which implies no access control checking.
     */
    private boolean authConstraint = false;


    /**
     * The set of roles permitted to access resources protected by this
     * security constraint.
     */
    private String authRoles[] = new String[0];


    /**
     * The set of web resource collections protected by this security
     * constraint.
     */
    private SecurityCollection collections[] = new SecurityCollection[0];


    /**
     * The display name of this security constraint.
     */
    private String displayName = null;


    /**
     * The user data constraint for this security constraint.  Must be NONE,
     * INTEGRAL, or CONFIDENTIAL.
     */
    private String userConstraint = "NONE";


    // ------------------------------------------------------------- Properties


    /**
     * Was the "all roles" wildcard included in this authentication
     * constraint?
     * @return <code>true</code> if all roles
     */
    public boolean getAllRoles() {

        return this.allRoles;

    }


    /**
     * Was the "all authenticated users" wildcard included in this
     * authentication constraint?
     * @return <code>true</code> if all authenticated users
     */
    public boolean getAuthenticatedUsers() {
        return this.authenticatedUsers;
    }


    /**
     * Return the authorization constraint present flag for this security
     * constraint.
     * @return <code>true</code> if this needs authorization
     */
    public boolean getAuthConstraint() {

        return this.authConstraint;

    }


    /**
     * Set the authorization constraint present flag for this security
     * constraint.
     * @param authConstraint The new value
     */
    public void setAuthConstraint(boolean authConstraint) {

        this.authConstraint = authConstraint;

    }


    /**
     * @return the display name of this security constraint.
     */
    public String getDisplayName() {

        return this.displayName;

    }


    /**
     * Set the display name of this security constraint.
     * @param displayName The new value
     */
    public void setDisplayName(String displayName) {

        this.displayName = displayName;

    }


    /**
     * Return the user data constraint for this security constraint.
     * @return the user constraint
     */
    public String getUserConstraint() {

        return userConstraint;

    }


    /**
     * Set the user data constraint for this security constraint.
     *
     * @param userConstraint The new user data constraint
     */
    public void setUserConstraint(String userConstraint) {

        if (userConstraint != null) {
            this.userConstraint = userConstraint;
        }

    }


    /**
     * Called in the unlikely event that an application defines a role named
     * "**".
     */
    public void treatAllAuthenticatedUsersAsApplicationRole() {
        if (authenticatedUsers) {
            authenticatedUsers = false;

            String[] results = Arrays.copyOf(authRoles, authRoles.length + 1);
            results[authRoles.length] = ROLE_ALL_AUTHENTICATED_USERS;
            authRoles = results;
            authConstraint = true;
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an authorization role, which is a role name that will be
     * permitted access to the resources protected by this security constraint.
     *
     * @param authRole Role name to be added
     */
    public void addAuthRole(String authRole) {

        if (authRole == null) {
            return;
        }

        if (ROLE_ALL_ROLES.equals(authRole)) {
            allRoles = true;
            return;
        }

        if (ROLE_ALL_AUTHENTICATED_USERS.equals(authRole)) {
            authenticatedUsers = true;
            return;
        }

        String[] results = Arrays.copyOf(authRoles, authRoles.length + 1);
        results[authRoles.length] = authRole;
        authRoles = results;
        authConstraint = true;
    }


    @Override
    public void setCharset(Charset charset) {
        super.setCharset(charset);
        for (SecurityCollection collection : collections) {
            collection.setCharset(getCharset());
        }
    }


    /**
     * Add a new web resource collection to those protected by this
     * security constraint.
     *
     * @param collection The new web resource collection
     */
    public void addCollection(SecurityCollection collection) {

        if (collection == null) {
            return;
        }

        collection.setCharset(getCharset());

        SecurityCollection results[] = Arrays.copyOf(collections, collections.length + 1);
        results[collections.length] = collection;
        collections = results;

    }


    /**
     * Check a role.
     *
     * @param role Role name to be checked
     * @return <code>true</code> if the specified role is permitted access to
     * the resources protected by this security constraint.
     */
    public boolean findAuthRole(String role) {

        if (role == null) {
            return false;
        }
        for (String authRole : authRoles) {
            if (role.equals(authRole)) {
                return true;
            }
        }
        return false;

    }


    /**
     * Return the set of roles that are permitted access to the resources
     * protected by this security constraint.  If none have been defined,
     * a zero-length array is returned (which implies that all authenticated
     * users are permitted access).
     * @return the roles array
     */
    public String[] findAuthRoles() {
        return authRoles;
    }


    /**
     * Return the web resource collection for the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Web resource collection name to return
     * @return the collection
     */
    public SecurityCollection findCollection(String name) {
        if (name == null) {
            return null;
        }
        for (SecurityCollection collection : collections) {
            if (name.equals(collection.getName())) {
                return collection;
            }
        }
        return null;
    }


    /**
     * Return all of the web resource collections protected by this
     * security constraint.  If there are none, a zero-length array is
     * returned.
     * @return the collections array
     */
    public SecurityCollection[] findCollections() {
        return collections;
    }


    /**
     * Check if the constraint applies to a URI and method.
     * @param uri Context-relative URI to check
     * @param method Request method being used
     * @return <code>true</code> if the specified context-relative URI (and
     * associated HTTP method) are protected by this security constraint.
     */
    public boolean included(String uri, String method) {

        // We cannot match without a valid request method
        if (method == null) {
            return false;
        }

        // Check all of the collections included in this constraint
        for (SecurityCollection collection : collections) {
            if (!collection.findMethod(method)) {
                continue;
            }
            String patterns[] = collection.findPatterns();
            for (String pattern : patterns) {
                if (matchPattern(uri, pattern)) {
                    return true;
                }
            }
        }

        // No collection included in this constraint matches this request
        return false;

    }


    /**
     * Remove the specified role from the set of roles permitted to access
     * the resources protected by this security constraint.
     *
     * @param authRole Role name to be removed
     */
    public void removeAuthRole(String authRole) {

        if (authRole == null) {
            return;
        }

        if (ROLE_ALL_ROLES.equals(authRole)) {
            allRoles = false;
            return;
        }

        if (ROLE_ALL_AUTHENTICATED_USERS.equals(authRole)) {
            authenticatedUsers = false;
            return;
        }

        int n = -1;
        for (int i = 0; i < authRoles.length; i++) {
            if (authRoles[i].equals(authRole)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[authRoles.length - 1];
            for (int i = 0; i < authRoles.length; i++) {
                if (i != n) {
                    results[j++] = authRoles[i];
                }
            }
            authRoles = results;
        }
    }


    /**
     * Remove the specified web resource collection from those protected by
     * this security constraint.
     *
     * @param collection Web resource collection to be removed
     */
    public void removeCollection(SecurityCollection collection) {

        if (collection == null) {
            return;
        }
        int n = -1;
        for (int i = 0; i < collections.length; i++) {
            if (collections[i].equals(collection)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            SecurityCollection results[] =
                new SecurityCollection[collections.length - 1];
            for (int i = 0; i < collections.length; i++) {
                if (i != n) {
                    results[j++] = collections[i];
                }
            }
            collections = results;
        }

    }


    /**
     * Return a String representation of this security constraint.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SecurityConstraint[");
        for (int i = 0; i < collections.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(collections[i].getName());
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Does the specified request path match the specified URL pattern?
     * This method follows the same rules (in the same order) as those used
     * for mapping requests to servlets.
     *
     * @param path Context-relative request path to be checked
     *  (must start with '/')
     * @param pattern URL pattern to be compared against
     */
    private boolean matchPattern(String path, String pattern) {

        // Normalize the argument strings
        if ((path == null) || (path.length() == 0)) {
            path = "/";
        }
        if ((pattern == null) || (pattern.length() == 0)) {
            pattern = "/";
        }

        // Check for exact match
        if (path.equals(pattern)) {
            return true;
        }

        // Check for path prefix matching
        if (pattern.startsWith("/") && pattern.endsWith("/*")) {
            pattern = pattern.substring(0, pattern.length() - 2);
            if (pattern.length() == 0)
             {
                return true;  // "/*" is the same as "/"
            }
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            while (true) {
                if (pattern.equals(path)) {
                    return true;
                }
                int slash = path.lastIndexOf('/');
                if (slash <= 0) {
                    break;
                }
                path = path.substring(0, slash);
            }
            return false;
        }

        // Check for suffix matching
        if (pattern.startsWith("*.")) {
            int slash = path.lastIndexOf('/');
            int period = path.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) &&
                path.endsWith(pattern.substring(1))) {
                return true;
            }
            return false;
        }

        // Check for universal mapping
        if (pattern.equals("/")) {
            return true;
        }

        return false;

    }


    /**
     * Convert a {@link ServletSecurityElement} to an array of
     * {@link SecurityConstraint}(s).
     *
     * @param element       The element to be converted
     * @param urlPattern    The url pattern that the element should be applied
     *                      to
     * @return              The (possibly zero length) array of constraints that
     *                      are the equivalent to the input
     */
    public static SecurityConstraint[] createConstraints(
            ServletSecurityElement element, String urlPattern) {
        Set<SecurityConstraint> result = new HashSet<>();

        // Add the per method constraints
        Collection<HttpMethodConstraintElement> methods =
            element.getHttpMethodConstraints();
        for (HttpMethodConstraintElement methodElement : methods) {
            SecurityConstraint constraint =
                createConstraint(methodElement, urlPattern, true);
            // There will always be a single collection
            SecurityCollection collection = constraint.findCollections()[0];
            collection.addMethod(methodElement.getMethodName());
            result.add(constraint);
        }

        // Add the constraint for all the other methods
        SecurityConstraint constraint = createConstraint(element, urlPattern, false);
        if (constraint != null) {
            // There will always be a single collection
            SecurityCollection collection = constraint.findCollections()[0];
            for (String name : element.getMethodNames()) {
                collection.addOmittedMethod(name);
            }

            result.add(constraint);

        }

        return result.toArray(new SecurityConstraint[0]);
    }

    private static SecurityConstraint createConstraint(
            HttpConstraintElement element, String urlPattern, boolean alwaysCreate) {

        SecurityConstraint constraint = new SecurityConstraint();
        SecurityCollection collection = new SecurityCollection();
        boolean create = alwaysCreate;

        if (element.getTransportGuarantee() !=
                ServletSecurity.TransportGuarantee.NONE) {
            constraint.setUserConstraint(element.getTransportGuarantee().name());
            create = true;
        }
        if (element.getRolesAllowed().length > 0) {
            String[] roles = element.getRolesAllowed();
            for (String role : roles) {
                constraint.addAuthRole(role);
            }
            create = true;
        }
        if (element.getEmptyRoleSemantic() != EmptyRoleSemantic.PERMIT) {
            constraint.setAuthConstraint(true);
            create = true;
        }

        if (create) {
            collection.addPattern(urlPattern);
            constraint.addCollection(collection);
            return constraint;
        }

        return null;
    }


    public static SecurityConstraint[] findUncoveredHttpMethods(
            SecurityConstraint[] constraints,
            boolean denyUncoveredHttpMethods, Log log) {

        Set<String> coveredPatterns = new HashSet<>();
        Map<String,Set<String>> urlMethodMap = new HashMap<>();
        Map<String,Set<String>> urlOmittedMethodMap = new HashMap<>();

        List<SecurityConstraint> newConstraints = new ArrayList<>();

        // First build the lists of covered patterns and those patterns that
        // might be uncovered
        for (SecurityConstraint constraint : constraints) {
            SecurityCollection[] collections = constraint.findCollections();
            for (SecurityCollection collection : collections) {
                String[] patterns = collection.findPatterns();
                String[] methods = collection.findMethods();
                String[] omittedMethods = collection.findOmittedMethods();
                // Simple case: no methods
                if (methods.length == 0 && omittedMethods.length == 0) {
                    coveredPatterns.addAll(Arrays.asList(patterns));
                    continue;
                }

                // Pre-calculate so we don't do this for every iteration of the
                // following loop
                List<String> omNew = null;
                if (omittedMethods.length != 0) {
                    omNew = Arrays.asList(omittedMethods);
                }

                // Only need to process uncovered patterns
                for (String pattern : patterns) {
                    if (!coveredPatterns.contains(pattern)) {
                        if (methods.length == 0) {
                            // Build the interset of omitted methods for this
                            // pattern
                            Set<String> om = urlOmittedMethodMap.get(pattern);
                            if (om == null) {
                                om = new HashSet<>();
                                urlOmittedMethodMap.put(pattern, om);
                                om.addAll(omNew);
                            } else {
                                om.retainAll(omNew);
                            }
                        } else {
                            // Build the union of methods for this pattern
                            urlMethodMap.computeIfAbsent(pattern, k -> new HashSet<>()).addAll(Arrays.asList(methods));
                        }
                    }
                }
            }
        }

        // Now check the potentially uncovered patterns
        for (Map.Entry<String, Set<String>> entry : urlMethodMap.entrySet()) {
            String pattern = entry.getKey();
            if (coveredPatterns.contains(pattern)) {
                // Fully covered. Ignore any partial coverage
                urlOmittedMethodMap.remove(pattern);
                continue;
            }

            Set<String> omittedMethods = urlOmittedMethodMap.remove(pattern);
            Set<String> methods = entry.getValue();

            if (omittedMethods == null) {
                StringBuilder msg = new StringBuilder();
                for (String method : methods) {
                    msg.append(method);
                    msg.append(' ');
                }
                if (denyUncoveredHttpMethods) {
                    log.info(sm.getString(
                            "securityConstraint.uncoveredHttpMethodFix",
                            pattern, msg.toString().trim()));
                    SecurityCollection collection = new SecurityCollection();
                    for (String method : methods) {
                        collection.addOmittedMethod(method);
                    }
                    collection.addPatternDecoded(pattern);
                    collection.setName("deny-uncovered-http-methods");
                    SecurityConstraint constraint = new SecurityConstraint();
                    constraint.setAuthConstraint(true);
                    constraint.addCollection(collection);
                    newConstraints.add(constraint);
                } else {
                    log.error(sm.getString(
                            "securityConstraint.uncoveredHttpMethod",
                            pattern, msg.toString().trim()));
                }
                continue;
            }

            // As long as every omitted method as a corresponding method the
            // pattern is fully covered.
            omittedMethods.removeAll(methods);

            handleOmittedMethods(omittedMethods, pattern, denyUncoveredHttpMethods,
                    newConstraints, log);
        }
        for (Map.Entry<String, Set<String>> entry :
                urlOmittedMethodMap.entrySet()) {
            String pattern = entry.getKey();
            if (coveredPatterns.contains(pattern)) {
                // Fully covered. Ignore any partial coverage
                continue;
            }

            handleOmittedMethods(entry.getValue(), pattern, denyUncoveredHttpMethods,
                    newConstraints, log);
        }

        return newConstraints.toArray(new SecurityConstraint[0]);
    }


    private static void handleOmittedMethods(Set<String> omittedMethods, String pattern,
            boolean denyUncoveredHttpMethods, List<SecurityConstraint> newConstraints, Log log) {
        if (omittedMethods.size() > 0) {
            StringBuilder msg = new StringBuilder();
            for (String method : omittedMethods) {
                msg.append(method);
                msg.append(' ');
            }
            if (denyUncoveredHttpMethods) {
                log.info(sm.getString(
                        "securityConstraint.uncoveredHttpOmittedMethodFix",
                        pattern, msg.toString().trim()));
                SecurityCollection collection = new SecurityCollection();
                for (String method : omittedMethods) {
                    collection.addMethod(method);
                }
                collection.addPatternDecoded(pattern);
                collection.setName("deny-uncovered-http-methods");
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setAuthConstraint(true);
                constraint.addCollection(collection);
                newConstraints.add(constraint);
            } else {
                log.error(sm.getString(
                        "securityConstraint.uncoveredHttpOmittedMethod",
                        pattern, msg.toString().trim()));
            }
        }
    }
}
