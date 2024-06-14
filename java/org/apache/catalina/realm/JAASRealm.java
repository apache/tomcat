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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountExpiredException;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.CredentialExpiredException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * <p>
 * Implementation of <b>Realm</b> that authenticates users via the <em>Java Authentication and Authorization
 * Service</em> (JAAS). JAAS support requires either JDK 1.4 (which includes it as part of the standard platform) or JDK
 * 1.3 (with the plug-in <code>jaas.jar</code> file).
 * </p>
 * <p>
 * The value configured for the <code>appName</code> property is passed to the
 * <code>javax.security.auth.login.LoginContext</code> constructor, to specify the <em>application name</em> used to
 * select the set of relevant <code>LoginModules</code> required.
 * </p>
 * <p>
 * The JAAS Specification describes the result of a successful login as a <code>javax.security.auth.Subject</code>
 * instance, which can contain zero or more <code>java.security.Principal</code> objects in the return value of the
 * <code>Subject.getPrincipals()</code> method. However, it provides no guidance on how to distinguish Principals that
 * describe the individual user (and are thus appropriate to return as the value of request.getUserPrincipal() in a web
 * application) from the Principal(s) that describe the authorized roles for this user. To maintain as much independence
 * as possible from the underlying <code>LoginMethod</code> implementation executed by JAAS, the following policy is
 * implemented by this Realm:
 * </p>
 * <ul>
 * <li>The JAAS <code>LoginModule</code> is assumed to return a <code>Subject</code> with at least one
 * <code>Principal</code> instance representing the user himself or herself, and zero or more separate
 * <code>Principals</code> representing the security roles authorized for this user.</li>
 * <li>On the <code>Principal</code> representing the user, the Principal name is an appropriate value to return via the
 * Servlet API method <code>HttpServletRequest.getRemoteUser()</code>.</li>
 * <li>On the <code>Principals</code> representing the security roles, the name is the name of the authorized security
 * role.</li>
 * <li>This Realm will be configured with two lists of fully qualified Java class names of classes that implement
 * <code>java.security.Principal</code> - one that identifies class(es) representing a user, and one that identifies
 * class(es) representing a security role.</li>
 * <li>As this Realm iterates over the <code>Principals</code> returned by <code>Subject.getPrincipals()</code>, it will
 * identify the first <code>Principal</code> that matches the "user classes" list as the <code>Principal</code> for this
 * user.</li>
 * <li>As this Realm iterates over the <code>Principals</code> returned by <code>Subject.getPrincipals()</code>, it will
 * accumulate the set of all <code>Principals</code> matching the "role classes" list as identifying the security roles
 * for this user.</li>
 * <li>It is a configuration error for the JAAS login method to return a validated <code>Subject</code> without a
 * <code>Principal</code> that matches the "user classes" list.</li>
 * <li>By default, the enclosing Container's name serves as the application name used to obtain the JAAS LoginContext
 * ("Catalina" in a default installation). Tomcat must be able to find an application with this name in the JAAS
 * configuration file. Here is a hypothetical JAAS configuration file entry for a database-oriented login module that
 * uses a Tomcat-managed JNDI database resource: <blockquote>
 *
 * <pre>
 * Catalina {
 * org.foobar.auth.DatabaseLoginModule REQUIRED
 *   JNDI_RESOURCE=jdbc/AuthDB
 *   USER_TABLE=users
 *   USER_ID_COLUMN=id
 *   USER_NAME_COLUMN=name
 *   USER_CREDENTIAL_COLUMN=password
 *   ROLE_TABLE=roles
 *   ROLE_NAME_COLUMN=name
 *   PRINCIPAL_FACTORY=org.foobar.auth.impl.SimplePrincipalFactory;
 * };
 * </pre>
 *
 * </blockquote></li>
 * <li>To set the JAAS configuration file location, set the <code>CATALINA_OPTS</code> environment variable similar to
 * the following:
 * <blockquote><code>CATALINA_OPTS="-Djava.security.auth.login.config=$CATALINA_HOME/conf/jaas.config"</code></blockquote>
 * </li>
 * <li>As part of the login process, JAASRealm registers its own <code>CallbackHandler</code>, called (unsurprisingly)
 * <code>JAASCallbackHandler</code>. This handler supplies the HTTP requests's username and credentials to the
 * user-supplied <code>LoginModule</code></li>
 * <li>As with other <code>Realm</code> implementations, digested passwords are supported if the
 * <code>&lt;Realm&gt;</code> element in <code>server.xml</code> contains a <code>digest</code> attribute;
 * <code>JAASCallbackHandler</code> will digest the password prior to passing it back to the
 * <code>LoginModule</code></li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Yoav Shapira
 */
public class JAASRealm extends RealmBase {

    private static final Log log = LogFactory.getLog(JAASRealm.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * The application name passed to the JAAS <code>LoginContext</code>, which uses it to select the set of relevant
     * <code>LoginModule</code>s.
     */
    protected String appName = null;


    /**
     * The list of role class names, split out for easy processing.
     */
    protected final List<String> roleClasses = new ArrayList<>();


    /**
     * The set of user class names, split out for easy processing.
     */
    protected final List<String> userClasses = new ArrayList<>();


    /**
     * Whether to use context ClassLoader or default ClassLoader. True means use context ClassLoader, and True is the
     * default value.
     */
    protected boolean useContextClassLoader = true;


    /**
     * Path to find a JAAS configuration file, if not set global JVM JAAS configuration will be used.
     */
    protected String configFile;

    protected volatile Configuration jaasConfiguration;
    protected volatile boolean jaasConfigurationLoaded = false;

    /**
     * Keeps track if JAAS invocation of login modules was successful or not. By default it is true unless we detect
     * JAAS login module can't perform the login. This will be used for realm's {@link #isAvailable()} status so that
     * {@link LockOutRealm} will not lock the user out if JAAS login modules are unavailable to perform the actual
     * login.
     */
    private volatile boolean invocationSuccess = true;

    // ------------------------------------------------------------- Properties

    /**
     * @return the path of the JAAS configuration file.
     */
    public String getConfigFile() {
        return configFile;
    }

    /**
     * Set the JAAS configuration file.
     *
     * @param configFile The JAAS configuration file
     */
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    /**
     * Set the JAAS <code>LoginContext</code> app name.
     *
     * @param name The application name that will be used to retrieve the set of relevant <code>LoginModule</code>s
     */
    public void setAppName(String name) {
        appName = name;
    }

    /**
     * @return the application name.
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Sets whether to use the context or default ClassLoader. True means use context ClassLoader.
     *
     * @param useContext True means use context ClassLoader
     */
    public void setUseContextClassLoader(boolean useContext) {
        useContextClassLoader = useContext;
    }

    /**
     * Returns whether to use the context or default ClassLoader. True means to use the context ClassLoader.
     *
     * @return The value of useContextClassLoader
     */
    public boolean isUseContextClassLoader() {
        return useContextClassLoader;
    }

    @Override
    public void setContainer(Container container) {
        super.setContainer(container);

        if (appName == null) {
            appName = makeLegalForJAAS(container.getName());
            log.info(sm.getString("jaasRealm.appName", appName));
        }
    }

    /**
     * Comma-delimited list of <code>java.security.Principal</code> classes that represent security roles.
     */
    protected String roleClassNames = null;

    public String getRoleClassNames() {
        return this.roleClassNames;
    }

    /**
     * Sets the list of comma-delimited classes that represent roles. The classes in the list must implement
     * <code>java.security.Principal</code>. The supplied list of classes will be parsed when {@link #start()} is
     * called.
     *
     * @param roleClassNames The class names list
     */
    public void setRoleClassNames(String roleClassNames) {
        this.roleClassNames = roleClassNames;
    }

    /**
     * Parses a comma-delimited list of class names, and store the class names in the provided List. Each class must
     * implement <code>java.security.Principal</code>.
     *
     * @param classNamesString a comma-delimited list of fully qualified class names.
     * @param classNamesList   the list in which the class names will be stored. The list is cleared before being
     *                             populated.
     */
    protected void parseClassNames(String classNamesString, List<String> classNamesList) {
        classNamesList.clear();
        if (classNamesString == null) {
            return;
        }

        ClassLoader loader = this.getClass().getClassLoader();
        if (isUseContextClassLoader()) {
            loader = Thread.currentThread().getContextClassLoader();
        }

        String[] classNames = classNamesString.split("[ ]*,[ ]*");
        for (String className : classNames) {
            if (className.length() == 0) {
                continue;
            }
            try {
                Class<?> principalClass = Class.forName(className, false, loader);
                if (Principal.class.isAssignableFrom(principalClass)) {
                    classNamesList.add(className);
                } else {
                    log.error(sm.getString("jaasRealm.notPrincipal", className));
                }
            } catch (ClassNotFoundException e) {
                log.error(sm.getString("jaasRealm.classNotFound", className));
            }
        }
    }

    /**
     * Comma-delimited list of <code>java.security.Principal</code> classes that represent individual users.
     */
    protected String userClassNames = null;

    public String getUserClassNames() {
        return this.userClassNames;
    }

    /**
     * Sets the list of comma-delimited classes that represent individual users. The classes in the list must implement
     * <code>java.security.Principal</code>. The supplied list of classes will be parsed when {@link #start()} is
     * called.
     *
     * @param userClassNames The class names list
     */
    public void setUserClassNames(String userClassNames) {
        this.userClassNames = userClassNames;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public Principal authenticate(String username, String credentials) {
        return authenticate(username, new JAASCallbackHandler(this, username, credentials));
    }


    @Override
    public Principal authenticate(String username, String clientDigest, String nonce, String nc, String cnonce,
            String qop, String realmName, String digestA2, String algorithm) {
        return authenticate(username, new JAASCallbackHandler(this, username, clientDigest, nonce, nc, cnonce, qop,
                realmName, digestA2, algorithm, HttpServletRequest.DIGEST_AUTH));
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Perform the actual JAAS authentication.
     *
     * @param username        The user name
     * @param callbackHandler The callback handler
     *
     * @return the associated principal, or <code>null</code> if there is none.
     */
    protected Principal authenticate(String username, CallbackHandler callbackHandler) {

        // Establish a LoginContext to use for authentication
        try {
            LoginContext loginContext = null;
            if (appName == null) {
                appName = "Tomcat";
            }

            if (log.isTraceEnabled()) {
                log.trace(sm.getString("jaasRealm.beginLogin", username, appName));
            }

            // What if the LoginModule is in the container class loader ?
            ClassLoader ocl = null;
            Thread currentThread = null;

            if (!isUseContextClassLoader()) {
                currentThread = Thread.currentThread();
                ocl = currentThread.getContextClassLoader();
                currentThread.setContextClassLoader(this.getClass().getClassLoader());
            }

            try {
                Configuration config = getConfig();
                loginContext = new LoginContext(appName, null, callbackHandler, config);
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("jaasRealm.unexpectedError"), e);
                // There is configuration issue with JAAS so mark the realm as
                // unavailable
                invocationSuccess = false;
                return null;
            } finally {
                if (currentThread != null) {
                    currentThread.setContextClassLoader(ocl);
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Login context created " + username);
            }

            // Negotiate a login via this LoginContext
            Subject subject = null;
            try {
                loginContext.login();
                subject = loginContext.getSubject();
                // We were able to perform login successfully so mark JAAS realm as
                // available as it could have been set to false in prior attempts.
                // Change invocationSuccess variable only when we know the outcome
                // of the JAAS operation to keep variable consistent.
                invocationSuccess = true;
                if (subject == null) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jaasRealm.failedLogin", username));
                    }
                    return null;
                }
            } catch (AccountExpiredException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.accountExpired", username));
                }
                // JAAS checked LoginExceptions are successful authentication
                // invocations so mark JAAS realm as available
                invocationSuccess = true;
                return null;
            } catch (CredentialExpiredException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.credentialExpired", username));
                }
                // JAAS checked LoginExceptions are successful authentication
                // invocations so mark JAAS realm as available
                invocationSuccess = true;
                return null;
            } catch (FailedLoginException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.failedLogin", username));
                }
                // JAAS checked LoginExceptions are successful authentication
                // invocations so mark JAAS realm as available
                invocationSuccess = true;
                return null;
            } catch (LoginException e) {
                log.warn(sm.getString("jaasRealm.loginException", username), e);
                // JAAS checked LoginExceptions are successful authentication
                // invocations so mark JAAS realm as available
                invocationSuccess = true;
                return null;
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("jaasRealm.unexpectedError"), e);
                // JAAS throws exception different than LoginException so mark the
                // realm as unavailable
                invocationSuccess = false;
                return null;
            }

            if (log.isTraceEnabled()) {
                log.trace(sm.getString("jaasRealm.loginContextCreated", username));
            }

            // Return the appropriate Principal for this authenticated Subject
            Principal principal = createPrincipal(username, subject, loginContext);
            if (principal == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.authenticateFailure", username));
                }
                return null;
            }
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("jaasRealm.authenticateSuccess", username, principal));
            }

            return principal;
        } catch (Throwable t) {
            log.error(sm.getString("jaasRealm.unexpectedError"), t);
            // JAAS throws exception different than LoginException so mark the realm as unavailable
            invocationSuccess = false;
            return null;
        }
    }


    /**
     * @return the password associated with the given principal's user name. This always returns null as the JAASRealm
     *             has no way of obtaining this information.
     */
    @Override
    protected String getPassword(String username) {
        return null;
    }


    @Override
    protected Principal getPrincipal(String username) {

        return authenticate(username, new JAASCallbackHandler(this, username, null, null, null, null, null, null, null,
                null, HttpServletRequest.CLIENT_CERT_AUTH));

    }


    /**
     * Identify and return a <code>java.security.Principal</code> instance representing the authenticated user for the
     * specified <code>Subject</code>. The Principal is constructed by scanning the list of Principals returned by the
     * JAASLoginModule. The first <code>Principal</code> object that matches one of the class names supplied as a "user
     * class" is the user Principal. This object is returned to the caller. Any remaining principal objects returned by
     * the LoginModules are mapped to roles, but only if their respective classes match one of the "role class" classes.
     * If a user Principal cannot be constructed, return <code>null</code>.
     *
     * @param username     The associated user name
     * @param subject      The <code>Subject</code> representing the logged-in user
     * @param loginContext Associated with the Principal so {@link LoginContext#logout()} can be called later
     *
     * @return the principal object
     */
    protected Principal createPrincipal(String username, Subject subject, LoginContext loginContext) {
        // Prepare to scan the Principals for this Subject

        List<String> roles = new ArrayList<>();
        Principal userPrincipal = null;

        // Scan the Principals for this Subject
        for (Principal principal : subject.getPrincipals()) {
            String principalClass = principal.getClass().getName();

            if (log.isTraceEnabled()) {
                log.trace(sm.getString("jaasRealm.checkPrincipal", principal, principalClass));
            }

            if (userPrincipal == null && userClasses.contains(principalClass)) {
                userPrincipal = principal;
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("jaasRealm.userPrincipalSuccess", principal.getName()));
                }
            }

            if (roleClasses.contains(principalClass)) {
                roles.add(principal.getName());
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("jaasRealm.rolePrincipalAdd", principal.getName()));
                }
            }
        }

        // Print failure message if needed
        if (userPrincipal == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jaasRealm.userPrincipalFailure"));
                log.debug(sm.getString("jaasRealm.rolePrincipalFailure"));
            }
            return null;
        } else {
            if (roles.size() == 0) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.rolePrincipalFailure"));
                }
            }
        }

        // Return the resulting Principal for our authenticated user
        return new GenericPrincipal(username, roles, userPrincipal, loginContext);
    }

    /**
     * Ensure the given name is legal for JAAS configuration. Added for Bugzilla 30869, made protected for easy
     * customization in case my implementation is insufficient, which I think is very likely.
     *
     * @param src The name to validate
     *
     * @return A string that's a valid JAAS realm name
     */
    protected String makeLegalForJAAS(final String src) {
        String result = src;

        // Default name is "other" per JAAS spec
        if (result == null) {
            result = "other";
        }

        // Strip leading slash if present, as Sun JAAS impl
        // barfs on it (see Bugzilla 30869 bug report).
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        return result;
    }


    // ------------------------------------------------------ Lifecycle Methods

    @Override
    protected void startInternal() throws LifecycleException {

        // These need to be called after loading configuration, in case
        // useContextClassLoader appears after them in xml config
        parseClassNames(userClassNames, userClasses);
        parseClassNames(roleClassNames, roleClasses);

        super.startInternal();
    }


    /**
     * Load custom JAAS Configuration.
     *
     * @return the loaded configuration
     */
    protected Configuration getConfig() {
        // Local copy to avoid possible NPE due to concurrent change
        String configFile = this.configFile;
        try {
            if (jaasConfigurationLoaded) {
                return jaasConfiguration;
            }
            synchronized (this) {
                if (configFile == null) {
                    jaasConfigurationLoaded = true;
                    return null;
                }
                URL resource = Thread.currentThread().getContextClassLoader().getResource(configFile);
                URI uri = resource.toURI();
                @SuppressWarnings("unchecked")
                Class<Configuration> sunConfigFile =
                        (Class<Configuration>) Class.forName("com.sun.security.auth.login.ConfigFile");
                Constructor<Configuration> constructor = sunConfigFile.getConstructor(URI.class);
                Configuration config = constructor.newInstance(uri);
                this.jaasConfiguration = config;
                this.jaasConfigurationLoaded = true;
                return this.jaasConfiguration;
            }
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex.getCause());
        } catch (SecurityException | URISyntaxException | ReflectiveOperationException | IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean isAvailable() {
        return invocationSuccess;
    }
}
