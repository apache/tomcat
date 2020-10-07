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

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;

import org.apache.catalina.Group;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.Wrapper;
import org.apache.naming.ContextBindings;
import org.apache.tomcat.util.ExceptionUtils;

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


    // --------------------------------------------------------- Public Methods

    /**
     * Return <code>true</code> if the specified Principal has the specified
     * security role, within the context of this Realm; otherwise return
     * <code>false</code>. This implementation returns <code>true</code> if the
     * <code>User</code> has the role, or if any <code>Group</code> that the
     * <code>User</code> is a member of has the role.
     *
     * @param principal Principal for whom the role is to be checked
     * @param role Security role to be checked
     */
    @Override
    public boolean hasRole(Wrapper wrapper, Principal principal, String role) {

        UserDatabase database = getUserDatabase();
        if (database == null) {
            return false;
        }

        // Check for a role alias defined in a <security-role-ref> element
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if (realRole != null)
                role = realRole;
        }
        if (principal instanceof GenericPrincipal) {
            GenericPrincipal gp = (GenericPrincipal) principal;
            if (gp.getUserPrincipal() instanceof UserDatabasePrincipal) {
                principal = database.findUser(gp.getName());
            }
        }
        if (!(principal instanceof User)) {
            // Play nice with SSO and mixed Realms
            // No need to pass the wrapper here because role mapping has been
            // performed already a few lines above
            return super.hasRole(null, principal, role);
        }
        if ("*".equals(role)) {
            return true;
        } else if (role == null) {
            return false;
        }
        User user = (User) principal;
        Role dbrole = database.findRole(role);
        if (dbrole == null) {
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
        }

        List<String> roles = new ArrayList<>();
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
        return new GenericPrincipal(username, roles,
                new UserDatabasePrincipal(username));
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
                        containerLog.error(sm.getString("userDatabaseRealm.lookup", resourceName), e);
                        database = null;
                    }
                }
            }
        }
        return database;
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


    private class UserDatabasePrincipal implements Principal {
        private final String name;
        private UserDatabasePrincipal(String name) {
            this.name = name;
        }
        @Override
        public String getName() {
            return name;
        }
    }
}
