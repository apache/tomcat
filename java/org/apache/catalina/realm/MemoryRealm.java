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
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.file.ConfigFileLoader;


/**
 * Simple implementation of <b>Realm</b> that reads an XML file to configure the valid users, passwords, and roles. The
 * file format (and default file location) are identical to those currently supported by Tomcat 3.X.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong>: It is assumed that the in-memory collection representing our defined users (and
 * their roles) is initialized at application startup and never modified again. Therefore, no thread synchronization is
 * performed around accesses to the principals collection.
 *
 * @author Craig R. McClanahan
 */
public class MemoryRealm extends RealmBase {

    private static final Log log = LogFactory.getLog(MemoryRealm.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * The Digester we will use to process in-memory database files.
     */
    private static Digester digester = null;
    private static final Object digesterLock = new Object();


    /**
     * The pathname (absolute or relative to Catalina's current working directory) of the XML file containing our
     * database information.
     */
    private String pathname = "conf/tomcat-users.xml";


    /**
     * The set of valid Principals for this Realm, keyed by user name.
     */
    private final Map<String,GenericPrincipal> principals = new HashMap<>();


    /**
     * The set of credentials for this Realm, keyed by user name.
     */
    private final Map<String,String> credentials = new HashMap<>();


    // ------------------------------------------------------------- Properties

    /**
     * @return the pathname of our XML file containing user definitions.
     */
    public String getPathname() {

        return pathname;

    }


    /**
     * Set the pathname of our XML file containing user definitions. If a relative pathname is specified, it is resolved
     * against "catalina.base".
     *
     * @param pathname The new pathname
     */
    public void setPathname(String pathname) {

        this.pathname = pathname;

    }


    // --------------------------------------------------------- Public Methods

    @Override
    public Principal authenticate(String username, String credentials) {

        // No user or no credentials
        // Can't possibly authenticate, don't bother the database then
        if (username == null || credentials == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            }
            return null;
        }

        GenericPrincipal principal = principals.get(username);
        String password = null;
        if (principal != null) {
            password = this.credentials.get(username);
        }

        if (principal == null || password == null) {
            // User was not found in the database or the password was null
            // Waste a bit of time as not to reveal that the user does not exist.
            getCredentialHandler().mutate(credentials);

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            }
            return null;
        }

        boolean validated = getCredentialHandler().matches(credentials, password);

        if (validated) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("memoryRealm.authenticateSuccess", username));
            }
            return principal;
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            }
            return null;
        }
    }


    // -------------------------------------------------------- Package Methods


    /**
     * Add a new user to the in-memory database.
     *
     * @param username User's username
     * @param password User's password (clear text)
     * @param roles    Comma-delimited set of roles associated with this user
     */
    void addUser(String username, String password, String roles) {

        // Accumulate the list of roles for this user
        List<String> list = new ArrayList<>();
        roles += ",";
        while (true) {
            int comma = roles.indexOf(',');
            if (comma < 0) {
                break;
            }
            String role = roles.substring(0, comma).trim();
            list.add(role);
            roles = roles.substring(comma + 1);
        }

        // Construct and cache the Principal for this user
        GenericPrincipal principal = new GenericPrincipal(username, list);
        principals.put(username, principal);
        credentials.put(username, password);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * @return a configured <code>Digester</code> to use for processing the XML input file, creating a new one if
     *             necessary.
     */
    protected Digester getDigester() {
        // Keep locking for subclass compatibility
        synchronized (digesterLock) {
            if (digester == null) {
                digester = new Digester();
                digester.setValidating(false);
                try {
                    digester.setFeature("http://apache.org/xml/features/allow-java-encodings", true);
                } catch (Exception e) {
                    log.warn(sm.getString("memoryRealm.xmlFeatureEncoding"), e);
                }
                digester.addRuleSet(new MemoryRuleSet());
            }
        }
        return digester;
    }


    @Override
    protected String getPassword(String username) {
        return credentials.get(username);
    }


    @Override
    protected Principal getPrincipal(String username) {
        return principals.get(username);
    }


    // ------------------------------------------------------ Lifecycle Methods

    @Override
    protected void startInternal() throws LifecycleException {
        String pathName = getPathname();
        try (InputStream is = ConfigFileLoader.getSource().getResource(pathName).getInputStream()) {
            // Load the contents of the database file
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("memoryRealm.loadPath", pathName));
            }

            synchronized (digesterLock) {
                Digester digester = getDigester();
                try {
                    digester.push(this);
                    digester.parse(is);
                } catch (Exception e) {
                    throw new LifecycleException(sm.getString("memoryRealm.readXml"), e);
                } finally {
                    digester.reset();
                }
            }
        } catch (IOException ioe) {
            throw new LifecycleException(sm.getString("memoryRealm.loadExist", pathName), ioe);
        }

        super.startInternal();
    }
}
