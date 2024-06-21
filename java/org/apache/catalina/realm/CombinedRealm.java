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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Wrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * Realm implementation that contains one or more realms. Authentication is attempted for each realm in the order they
 * were configured. If any realm authenticates the user then the authentication succeeds. When combining realms
 * usernames should be unique across all combined realms.
 */
public class CombinedRealm extends RealmBase {

    private static final Log log = LogFactory.getLog(CombinedRealm.class);

    /**
     * The list of Realms contained by this Realm.
     */
    protected final List<Realm> realms = new ArrayList<>();

    /**
     * Add a realm to the list of realms that will be used to authenticate users.
     *
     * @param theRealm realm which should be wrapped by the combined realm
     */
    public void addRealm(Realm theRealm) {
        realms.add(theRealm);

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("combinedRealm.addRealm", theRealm.getClass().getName(),
                    Integer.toString(realms.size())));
        }
    }


    /**
     * @return the set of Realms that this Realm is wrapping
     */
    public ObjectName[] getRealms() {
        ObjectName[] result = new ObjectName[realms.size()];
        for (Realm realm : realms) {
            if (realm instanceof RealmBase) {
                result[realms.indexOf(realm)] = ((RealmBase) realm).getObjectName();
            }
        }
        return result;
    }


    /**
     * @return the list of Realms contained by this Realm.
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
            // Set the realmPath for JMX naming
            if (realm instanceof RealmBase) {
                ((RealmBase) realm).setRealmPath(getRealmPath() + "/realm" + realms.indexOf(realm));
            }

            // Set the container for sub-realms. Mainly so logging works.
            realm.setContainer(container);
        }
        super.setContainer(container);
    }


    @Override
    protected void startInternal() throws LifecycleException {
        // Start 'sub-realms' then this one
        Iterator<Realm> iter = realms.iterator();

        while (iter.hasNext()) {
            Realm realm = iter.next();
            if (realm instanceof Lifecycle) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    // If realm doesn't start can't authenticate against it
                    iter.remove();
                    log.error(sm.getString("combinedRealm.realmStartFail", realm.getClass().getName()), e);
                }
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
            if (realm instanceof Lifecycle) {
                ((Lifecycle) realm).stop();
            }
        }
    }


    /**
     * Ensure child Realms are destroyed when this Realm is destroyed.
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        for (Realm realm : realms) {
            if (realm instanceof Lifecycle) {
                ((Lifecycle) realm).destroy();
            }
        }
        super.destroyInternal();
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
            GSSName gssName = null;
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
        log.error(sm.getString("combinedRealm.unexpectedMethod"), uoe);
        throw uoe;
    }

    @Override
    protected Principal getPrincipal(String username) {
        // This method should never be called
        // Stack trace will show where this was called from
        UnsupportedOperationException uoe =
                new UnsupportedOperationException(sm.getString("combinedRealm.getPrincipal"));
        log.error(sm.getString("combinedRealm.unexpectedMethod"), uoe);
        throw uoe;
    }


    @Override
    public boolean isAvailable() {
        for (Realm realm : realms) {
            if (!realm.isAvailable()) {
                return false;
            }
        }
        return true;
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
