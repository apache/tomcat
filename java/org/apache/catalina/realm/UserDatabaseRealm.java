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

import java.io.ObjectStreamException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;

import org.apache.catalina.Group;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.naming.ContextBindings;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringUtils;

/**
 * Implementation of {@link org.apache.catalina.Realm} that is based on an
 * implementation of {@link UserDatabase} made available through the JNDI
 * resources configured for this instance of Catalina. Set the
 * <code>resourceName</code> parameter to the JNDI resources name for the
 * configured instance of <code>UserDatabase</code> that we should consult.
 *
 * @author Craig R. McClanahan
 * @since 4.1
 */
public class UserDatabaseRealm extends RealmBase {

    /**
     * Contains the names of all user attributes available for this Realm.
     */
    private static final List<String> USER_ATTRIBUTES_AVAILABLE =
            new ArrayList<>(Arrays.asList("username", "fullname", "groups",
                    "roles", "effectiveRoles"));

    /**
     * Contains the names of user attributes for which access is denied.
     */
    private static final List<String> USER_ATTRIBUTES_ACCESS_DENIED =
            new ArrayList<>(Arrays.asList("password"));


    // ----------------------------------------------------- Instance Variables

    /**
     * The <code>UserDatabase</code> we will use to authenticate users and
     * identify associated roles.
     */
    protected volatile UserDatabase database = null;
    private final Object databaseLock = new Object();

    /**
     * The global JNDI name of the <code>UserDatabase</code> resource we will be
     * utilizing.
     */
    protected String resourceName = "UserDatabase";

    /**
     * Obtain the UserDatabase from the context (rather than global) JNDI.
     */
    private boolean localJndiResource = false;

    /**
     * Use a static principal disconnected from the database. This prevents live
     * updates to users and roles having an effect on authenticated principals,
     * but reduces use of the database.
     */
    private boolean useStaticPrincipal = false;

    /**
     * The comma separated names of user attributes to additionally query from
     * the <code>User</code> entry of the underlying <code>UserDatabase</code>.
     * These will be provided to the user through the created Principal's
     * <i>attributes</i> map.
     */
    protected String userAttributes;

    /**
     * Generated list of names of user attributes to additionally query from the
     * <code>User</code> entry of the underlying <code>UserDatabase</code>
     * (parsed and with wildcards (*) resolved).
     */
    private volatile List<String> userAttributesList;
    private final Object userAttributesListLock = new Object();

    // ------------------------------------------------------------- Properties

    /**
     * @return the global JNDI name of the <code>UserDatabase</code> resource we
     *         will be using.
     */
    public String getResourceName() {
        return resourceName;
    }


    /**
     * Set the global JNDI name of the <code>UserDatabase</code> resource we
     * will be using.
     *
     * @param resourceName The new global JNDI name
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }


    /**
     * @return the useStaticPrincipal flag
     */
    public boolean getUseStaticPrincipal() {
        return this.useStaticPrincipal;
    }


    /**
     * Allows using a static principal disconnected from the user database.
     * @param useStaticPrincipal the new value
     */
    public void setUseStaticPrincipal(boolean useStaticPrincipal) {
        this.useStaticPrincipal = useStaticPrincipal;
    }


    /**
     * Determines whether this Realm is configured to obtain the associated
     * {@link UserDatabase} from the global JNDI context or a local (web
     * application) JNDI context.
     *
     * @return {@code true} if a local JNDI context will be used, {@code false}
     *         if the the global JNDI context will be used
     */
    public boolean getLocalJndiResource() {
        return localJndiResource;
    }


    /**
     * Configure whether this Realm obtains the associated {@link UserDatabase}
     * from the global JNDI context or a local (web application) JNDI context.
     *
     * @param localJndiResource {@code true} to use a local JNDI context,
     *                          {@code false} to use the global JNDI context
     */
    public void setLocalJndiResource(boolean localJndiResource) {
        this.localJndiResource = localJndiResource;
    }

    /**
     * Return the comma separated names of user attributes to additionally query
     * from the <code>User</code> entry of the underlying <code>UserDatabase</code>
     */
    public String getUserAttributes() {
        return userAttributes;
    }

    /**
     * Set the comma separated names of user attributes to additionally query from
     * the <code>User</code> entry of the underlying <code>UserDatabase</code>.
     * These will be provided to the user through the created Principal's
     * <i>attributes</i> map. In this map, each attribute value is bound to the
     * attributes's name, that is, the name of the attribute serves as the key of
     * the mapping.
     * <p>
     * If set to the wildcard character, or, if the wildcard character is part of
     * the comma separated list, all available attributes - except the
     * <i>password</i> attribute - are queried. The wildcard character is defined by
     * constant {@link RealmBase#USER_ATTRIBUTES_WILDCARD}. It defaults to the
     * asterisk (*) character.
     * <p>
     * With the <code>UserDatabaseRealm</code>, the only attribute names supported
     * are:
     * <table>
     * <caption>&nbsp;</caption>
     * <tr>
     * <td>username</td>
     * <td>The user's logon name</td>
     * </tr>
     * <tr>
     * <td>fullname</td>
     * <td>The user's full name (aka display name)</td>
     * </tr>
     * <tr>
     * <td>groups</td>
     * <td>Comma separated list of groups the user is a member of</td>
     * </tr>
     * <tr>
     * <td>roles</td>
     * <td>Comma separated list of roles explicitly assigned to the user</td>
     * </tr>
     * <tr>
     * <td style="padding-right:10px">effectiveRoles</td>
     * <td>Comma separated list of effective roles assigned to the user</td>
     * </tr>
     * </table>
     *
     * @param userAttributes the comma separated names of user attributes
     */
    public void setUserAttributes(String userAttributes) {
        this.userAttributes = userAttributes;
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    public void backgroundProcess() {
        UserDatabase database = getUserDatabase();
        if (database != null) {
            database.backgroundProcess();
        }
    }


    /**
     * Return the password associated with the given principal's user name.
     */
    @Override
    protected String getPassword(String username) {
        UserDatabase database = getUserDatabase();
        if (database == null) {
            return null;
        }

        User user = database.findUser(username);

        if (user == null) {
            return null;
        }

        return user.getPassword();
    }


    public static String[] getRoles(User user) {
        Set<String> roles = new HashSet<>();
        Iterator<Role> uroles = user.getRoles();
        while (uroles.hasNext()) {
            Role role = uroles.next();
            roles.add(role.getName());
        }
        Iterator<Group> groups = user.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            uroles = group.getRoles();
            while (uroles.hasNext()) {
                Role role = uroles.next();
                roles.add(role.getName());
            }
        }
        return roles.toArray(new String[0]);
    }


    /**
     * Return the Principal associated with the given user name.
     */
    @Override
    protected Principal getPrincipal(String username) {
        UserDatabase database = getUserDatabase();
        if (database == null) {
            return null;
        }
        User user = database.findUser(username);
        if (user == null) {
            return null;
        } else {
            if (useStaticPrincipal) {
                return new GenericPrincipal(username, Arrays.asList(getRoles(user)));
            } else {
                return new UserDatabasePrincipal(user, database);
            }
        }
    }


    /*
     * Can't do this in startInternal() with local JNDI as the local JNDI
     * context won't be initialised at this point.
     */
    private UserDatabase getUserDatabase() {
        // DCL so database MUST be volatile
        if (database == null) {
            synchronized (databaseLock) {
                if (database == null) {
                    try {
                        Context context = null;
                        if (localJndiResource) {
                            context = ContextBindings.getClassLoader();
                            context = (Context) context.lookup("comp/env");
                        } else {
                            context = getServer().getGlobalNamingContext();
                        }
                        database = (UserDatabase) context.lookup(resourceName);
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        if (containerLog != null) {
                            containerLog.error(sm.getString("userDatabaseRealm.lookup", resourceName), e);
                        }
                        database = null;
                    }
                }
            }
        }
        return database;
    }


    private List<String> getUserAttributesList() {
        // DCL so userAttributesList MUST be volatile
        if (userAttributesList == null) {
            synchronized (userAttributesListLock) {
                if (userAttributesList == null) {
                    userAttributesList = parseUserAttributes(userAttributes,
                            USER_ATTRIBUTES_ACCESS_DENIED, USER_ATTRIBUTES_AVAILABLE, true);
                }
            }
        }
        return userAttributesList;
    }


    // ------------------------------------------------------ Lifecycle Methods

    @Override
    protected void startInternal() throws LifecycleException {
        // If the JNDI resource is global, check it here and fail the context
        // start if it is not valid. Local JNDI resources can't be validated
        // this way because the JNDI context isn't available at Realm start.
        if (!localJndiResource) {
            UserDatabase database = getUserDatabase();
            if (database == null) {
                throw new LifecycleException(sm.getString("userDatabaseRealm.noDatabase", resourceName));
            }
        }

        super.startInternal();
    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *                that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Perform normal superclass finalization
        super.stopInternal();

        // Release reference to our user database
        database = null;
    }


    @Override
    public boolean isAvailable() {
        return (database == null) ? false : database.isAvailable();
    }


    public static final class UserDatabasePrincipal extends GenericPrincipal {
        private static final long serialVersionUID = 1L;
        private final transient UserDatabase database;
        private final List<String> userAttributesList;

        public UserDatabasePrincipal(User user, UserDatabase database) {
            super(user.getName());
            this.database = database;
            userAttributesList = UserDatabaseRealm.this.getUserAttributesList();
        }

        @Override
        public String[] getRoles() {
            if (database == null) {
                return new String[0];
            }
            User user = database.findUser(name);
            if (user == null) {
                return new String[0];
            }
            Set<String> roles = new HashSet<>();
            Iterator<Role> uroles = user.getRoles();
            while (uroles.hasNext()) {
                Role role = uroles.next();
                roles.add(role.getName());
            }
            Iterator<Group> groups = user.getGroups();
            while (groups.hasNext()) {
                Group group = groups.next();
                uroles = group.getRoles();
                while (uroles.hasNext()) {
                    Role role = uroles.next();
                    roles.add(role.getName());
                }
            }
            return roles.toArray(new String[0]);
        }

        @Override
        public boolean hasRole(String role) {
            if ("*".equals(role)) {
                return true;
            } else if (role == null) {
                return false;
            }
            if (database == null) {
                return super.hasRole(role);
            }
            Role dbrole = database.findRole(role);
            if (dbrole == null) {
                return false;
            }
            User user = database.findUser(name);
            if (user == null) {
                return false;
            }
            if (user.isInRole(dbrole)) {
                return true;
            }
            Iterator<Group> groups = user.getGroups();
            while (groups.hasNext()) {
                Group group = groups.next();
                if (group.isInRole(dbrole)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object getAttribute(String name) {
            if (userAttributesList == null || !userAttributesList.contains(name)) {
                // Return only requested attributes 
                return null;
            }
            UserDatabase database = getUserDatabase();
            if (user == null || database == null) {
                return super.getAttribute(name);
            }
            StringBuilder sb;
            boolean first = true;
            switch (name) {
            case "username":
            case "name":
                return new String(user.getUsername());

            case "fullname":
                return new String(user.getFullName());

            case "groups":
                sb = new StringBuilder();
                Iterator<Group> groups = user.getGroups();
                while (groups.hasNext()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    Group group = groups.next();
                    sb.append(group.getName());
                }
                return sb.toString();

            case "roles":
                sb = new StringBuilder();
                Iterator<Role> roles = user.getRoles();
                while (roles.hasNext()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    Role role = roles.next();
                    sb.append(role.getName());
                }
                return sb.toString();

            case "effectiveRoles":
                return StringUtils.join(getRoles());
            }
            return null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(userAttributesList);
        }

        /**
         * Magic method from {@link java.io.Serializable}.
         *
         * @return The object to serialize instead of this object
         *
         * @throws ObjectStreamException Not thrown by this implementation
         */
        private Object writeReplace() throws ObjectStreamException {
            // Replace with a static principal disconnected from the database
            return new GenericPrincipal(getName(), Arrays.asList(getRoles()),
                    null, null, null, getUserAttributesMap());
        }

        private Map<String, Object> getUserAttributesMap() {
            if (userAttributesList == null) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (String attr : userAttributesList) {
                result.put(attr, getAttribute(attr));
            }
            return result;
        }
    }
}
