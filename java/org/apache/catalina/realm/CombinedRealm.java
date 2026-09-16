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
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * Realm implementation that contains one or more realms. Authentication is attempted for each realm in the order they
 * were configured. If any realm authenticates the user then the authentication succeeds. When combining realms user
 * names should be unique across all combined realms.
 * <p>
 * For the typical usage (create realms, add realms to container, start container), each realm will be registered with
 * JMX and {@link #getRealmPath()} will match the JMX object name. For simple changes (remove a realm, add a new realm)
 * {@link #getRealmPath()} and the JMX object name will remain synchronized. For more unusual changes such as moving the
 * realm to a new container, the JMX object names and {@link #getRealmPath()} are very likely to end up out of sync. If
 * there are naming conflicts it is possible for realms that are still in use to not be registered in JMX.
 */
public class CombinedRealm extends RealmBase {

    private static final Log log = LogFactory.getLog(CombinedRealm.class);

    /**
     * The list of Realms contained by this Realm.
     */
    protected final List<Realm> realms = new CopyOnWriteArrayList<>();

    private final Set<Realm> realmsToDestroy = new HashSet<>();

    private int nextRealmIndex = 0;


    /**
     * Default constructor for CombinedRealm.
     */
    public CombinedRealm() {
    }


    /**
     * Add a realm to the list of realms that will be used to authenticate users.
     *
     * @param realm Realm which should be added to the combined realm
     */
    public synchronized void addRealm(Realm realm) {

        if (realms.contains(realm)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.addRealmDuplicate", realm));
            }
            return;
        }

        boolean addRealm = true;

        setSubRealmPath(realm);

        if (getState().isAvailable()) {
            addRealm = startRealm(realm);
        }
        if (addRealm) {
            nextRealmIndex++;
            realms.add(realm);
            // In case the Realm has been removed and then re-added
            realmsToDestroy.remove(realm);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.addRealm", realm.getClass().getName(),
                        Integer.toString(realms.size())));
            }
        } else {
            destroyRealm(realm);
        }
    }


    private boolean startRealm(Realm realm) {
        realm.setContainer(getContainer());
        if (realm instanceof Lifecycle) {
            try {
                ((Lifecycle) realm).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("combinedRealm.realmStartFail", realm.getClass().getName()), e);
                return false;
            }
        }
        return true;
    }


    /**
     * Remove a realm from the list of realms that are used to authenticate users.
     *
     * @param realm Realm which should be removed from the combined realm
     *
     * @return {@code true} if the realm was present and removed, {@code false} otherwise
     */
    public synchronized boolean removeRealm(Realm realm) {
        boolean removed = realms.remove(realm);

        if (removed) {
            if (getState().isAvailable()) {
                /*
                 * This realm could be being used in an authenticate() call. Delay destroying it until after the
                 * combined realm has stopped.
                 */
                realmsToDestroy.add(realm);
            } else {
                destroyRealm(realm);
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.removeRealm", realm.getClass().getName(),
                        Integer.toString(realms.size())));
            }
        }
        return removed;
    }


    /**
     * Returns the JMX ObjectNames of the realms that this realm is wrapping. Entries for realms that do not implement
     * LifecycleMBeanBase will be null.
     *
     * @return the array of realm ObjectNames, which may contain null entries
     */
    public ObjectName[] getRealms() {
        Realm[] realmsSnapshot = getNestedRealms();
        ObjectName[] result = new ObjectName[realmsSnapshot.length];
        int i = 0;
        for (Realm realm : realmsSnapshot) {
            if (realm instanceof LifecycleMBeanBase) {
                result[i] = ((LifecycleMBeanBase) realm).getObjectName();
            }
            i++;
        }
        return result;
    }


    /**
     * Returns the array of Realm instances contained by this realm.
     *
     * @return the array of nested realms
     */
    public Realm[] getNestedRealms() {
        return realms.toArray(new Realm[0]);
    }


    @Override
    public Principal authenticate(String username, String clientDigest, String nonce, String nc, String cnonce,
            String qop, String realmName, String digestA2, String algorithm) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("combinedRealm.authStart", username, realm.getClass().getName()));
            }

            authenticatedUser =
                    realm.authenticate(username, clientDigest, nonce, nc, cnonce, qop, realmName, digestA2, algorithm);

            if (authenticatedUser == null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authFail", username, realm.getClass().getName()));
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authSuccess", username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    @Override
    public Principal authenticate(String username) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("combinedRealm.authStart", username, realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(username);

            if (authenticatedUser == null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authFail", username, realm.getClass().getName()));
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authSuccess", username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    @Override
    public Principal authenticate(String username, String credentials) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("combinedRealm.authStart", username, realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(username, credentials);

            if (authenticatedUser == null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authFail", username, realm.getClass().getName()));
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authSuccess", username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    @Override
    public void setContainer(Container container) {
        for (Realm realm : realms) {
            // Set the container for sub-realms. Mainly so logging works.
            realm.setContainer(container);
        }
        super.setContainer(container);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Calling this method will also (re)set the paths for all of the nested realms. If a nested realm has been removed
     * this will result in the remaining nested realms being re-numbered which may create an inconsistency between the
     * nested realm's path and its JMX registration (if any).
     */
    @Override
    public synchronized void setRealmPath(String theRealmPath) {
        super.setRealmPath(theRealmPath);
        nextRealmIndex = 0;
        for (Realm realm : realms) {
            setSubRealmPath(realm);
            nextRealmIndex++;
        }
    }


    private void setSubRealmPath(Realm realm) {
        if (realm instanceof RealmBase) {
            ((RealmBase) realm).setRealmPath(getRealmPath() + "/realm" + Integer.toString(nextRealmIndex));
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {
        // Start 'sub-realms' then this one
        for (Realm realm : realms) {
            if (!startRealm(realm)) {
                removeRealm(realm);
            }
        }

        if (getCredentialHandler() == null) {
            // Set a credential handler that will ask the nested realms so that it can
            // be set by the context in the attributes, it won't be used directly
            super.setCredentialHandler(new CombinedRealmCredentialHandler());
        }
        super.startInternal();
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        // Stop this realm, then the sub-realms (reverse order to start)
        super.stopInternal();
        for (Realm realm : realms) {
            stopRealm(realm);
        }
        for (Realm realm : realmsToDestroy) {
            stopRealm(realm);
        }
    }


    private void stopRealm(Realm realm) {
        if (realm instanceof Lifecycle) {
            if (((Lifecycle) realm).getState().isAvailable()) {
                try {
                    ((Lifecycle) realm).stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("combinedRealm.realmStopFail", realm.getClass().getName()), e);
                }
            }
        }
    }


    /**
     * Ensure child Realms are destroyed when this Realm is destroyed.
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        for (Realm realm : realms) {
            destroyRealm(realm);
        }
        super.destroyInternal();

        for (Realm realm : realmsToDestroy) {
            destroyRealm(realm);
        }
    }


    private void destroyRealm(Realm realm) {
        stopRealm(realm);
        if (realm instanceof Lifecycle) {
            try {
                ((Lifecycle) realm).destroy();
            } catch (LifecycleException e) {
                log.error(sm.getString("combinedRealm.realmDestroyFail", realm.getClass().getName()), e);
            }
        }
    }


    /**
     * Delegate the backgroundProcess call to all sub-realms.
     */
    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        for (Realm r : realms) {
            r.backgroundProcess();
        }
    }


    @Override
    public Principal authenticate(X509Certificate[] certs) {
        Principal authenticatedUser = null;
        String username = null;
        if (certs != null && certs.length > 0) {
            username = certs[0].getSubjectX500Principal().toString();
        }

        for (Realm realm : realms) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("combinedRealm.authStart", username, realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(certs);

            if (authenticatedUser == null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authFail", username, realm.getClass().getName()));
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authSuccess", username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCred) {
        if (gssContext.isEstablished()) {
            Principal authenticatedUser = null;
            GSSName gssName;
            try {
                gssName = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
                return null;
            }

            for (Realm realm : realms) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authStart", gssName, realm.getClass().getName()));
                }

                authenticatedUser = realm.authenticate(gssContext, storeCred);

                if (authenticatedUser == null) {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("combinedRealm.authFail", gssName, realm.getClass().getName()));
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("combinedRealm.authSuccess", gssName, realm.getClass().getName()));
                    }
                    break;
                }
            }
            return authenticatedUser;
        }

        // Fail in all other cases
        return null;
    }


    @Override
    public Principal authenticate(GSSName gssName, GSSCredential gssCredential) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("combinedRealm.authStart", gssName, realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(gssName, gssCredential);

            if (authenticatedUser == null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authFail", gssName, realm.getClass().getName()));
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("combinedRealm.authSuccess", gssName, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    @Override
    public boolean hasRole(Wrapper wrapper, Principal principal, String role) {
        for (Realm realm : realms) {
            if (realm.hasRole(wrapper, principal, role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String getPassword(String username) {
        // This method should never be called
        // Stack trace will show where this was called from
        UnsupportedOperationException uoe =
                new UnsupportedOperationException(sm.getString("combinedRealm.getPassword"));
        log.error(uoe.getMessage(), uoe);
        throw uoe;
    }

    @Override
    protected Principal getPrincipal(String username) {
        // This method should never be called
        // Stack trace will show where this was called from
        UnsupportedOperationException uoe =
                new UnsupportedOperationException(sm.getString("combinedRealm.getPrincipal"));
        log.error(uoe.getMessage(), uoe);
        throw uoe;
    }


    @Override
    public boolean isAvailable() {
        for (Realm realm : realms) {
            if (realm.isAvailable()) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void setCredentialHandler(CredentialHandler credentialHandler) {
        // This is unusual for a CombinedRealm as it does not use
        // CredentialHandlers. It might be a mis-configuration so warn the user.
        log.warn(sm.getString("combinedRealm.setCredentialHandler"));
        super.setCredentialHandler(credentialHandler);
    }

    private class CombinedRealmCredentialHandler implements CredentialHandler {

        @Override
        public boolean matches(String inputCredentials, String storedCredentials) {
            for (Realm realm : realms) {
                if (realm.getCredentialHandler().matches(inputCredentials, storedCredentials)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String mutate(String inputCredentials) {
            if (realms.isEmpty()) {
                return null;
            }
            for (Realm realm : realms) {
                String mutatedCredentials = realm.getCredentialHandler().mutate(inputCredentials);
                if (mutatedCredentials != null) {
                    return mutatedCredentials;
                }
            }
            return null;
        }

    }
}
