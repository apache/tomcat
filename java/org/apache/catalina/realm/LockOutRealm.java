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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * This class extends the CombinedRealm (hence it can wrap other Realms) to provide a user lock out mechanism if there
 * are too many failed authentication attempts in a given period of time. To ensure correct operation, there is a
 * reasonable degree of synchronisation in this Realm. This Realm does not require modification to the underlying Realms
 * or the associated user storage mechanisms. It achieves this by recording all failed logins, including those for users
 * that do not exist. To prevent a DOS by deliberating making requests with invalid users (and hence causing this cache
 * to grow) the size of the list of users that have failed authentication is limited.
 */
public class LockOutRealm extends CombinedRealm {

    private static final Log log = LogFactory.getLog(LockOutRealm.class);

    /**
     * The number of times in a row a user has to fail authentication to be locked out. Defaults to 5.
     */
    protected int failureCount = 5;

    /**
     * The time (in seconds) a user is locked out for after too many authentication failures. Defaults to 300 (5
     * minutes).
     */
    protected int lockOutTime = 300;

    /**
     * Number of users that have failed authentication to keep in cache. Over time the cache will grow to this size and
     * may not shrink. Defaults to 1000.
     */
    protected int cacheSize = 1000;

    /**
     * If a failed user is removed from the cache because the cache is too big before it has been in the cache for at
     * least this period of time (in seconds) a warning message will be logged. Defaults to 3600 (1 hour).
     */
    protected int cacheRemovalWarningTime = 3600;

    /**
     * Users whose last authentication attempt failed. Entries will be ordered in access order from least recent to most
     * recent.
     */
    protected Map<String,LockRecord> failedUsers = null;


    @Override
    protected void startInternal() throws LifecycleException {
        /*
         * Configure the list of failed users to delete the oldest entry once it exceeds the specified size. This is an
         * LRU cache so if the cache size is exceeded the users who most recently failed authentication will be
         * retained.
         */
        failedUsers = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String,LockRecord> eldest) {
                if (size() > cacheSize) {
                    // Check to see if this element has been removed too quickly
                    long timeInCache = (System.currentTimeMillis() - eldest.getValue().getLastFailureTime()) / 1000;

                    if (timeInCache < cacheRemovalWarningTime) {
                        log.warn(
                                sm.getString("lockOutRealm.removeWarning", eldest.getKey(), Long.valueOf(timeInCache)));
                    }
                    return true;
                }
                return false;
            }
        };

        super.startInternal();
    }


    @Override
    public Principal authenticate(String username, String clientDigest, String nonce, String nc, String cnonce,
            String qop, String realmName, String digestA2, String algorithm) {

        Principal authenticatedUser =
                super.authenticate(username, clientDigest, nonce, nc, cnonce, qop, realmName, digestA2, algorithm);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(String username, String credentials) {
        Principal authenticatedUser = super.authenticate(username, credentials);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(X509Certificate[] certs) {
        String username = null;
        if (certs != null && certs.length > 0) {
            username = certs[0].getSubjectX500Principal().toString();
        }

        Principal authenticatedUser = super.authenticate(certs);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCreds) {
        if (gssContext.isEstablished()) {
            String username = null;
            GSSName name = null;
            try {
                name = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
                return null;
            }

            username = name.toString();

            Principal authenticatedUser = super.authenticate(gssContext, storeCreds);

            return filterLockedAccounts(username, authenticatedUser);
        }

        // Fail in all other cases
        return null;
    }


    @Override
    public Principal authenticate(GSSName gssName, GSSCredential gssCredential) {
        String username = gssName.toString();

        Principal authenticatedUser = super.authenticate(gssName, gssCredential);

        return filterLockedAccounts(username, authenticatedUser);
    }


    /*
     * Filters authenticated principals to ensure that <code>null</code> is returned for any user that is currently
     * locked out.
     */
    private Principal filterLockedAccounts(String username, Principal authenticatedUser) {
        // Register all failed authentications
        if (authenticatedUser == null && isAvailable()) {
            registerAuthFailure(username);
        }

        if (isLocked(username)) {
            // If the user is currently locked, authentication will always fail
            log.warn(sm.getString("lockOutRealm.authLockedUser", username));
            return null;
        }

        if (authenticatedUser != null) {
            registerAuthSuccess(username);
        }

        return authenticatedUser;
    }


    /**
     * Unlock the specified username. This will remove all records of authentication failures for this user.
     *
     * @param username The user to unlock
     */
    public void unlock(String username) {
        // Auth success clears the lock record so...
        registerAuthSuccess(username);
    }


    /*
     * Checks to see if the current user is locked. If this is associated with a login attempt, then the last access
     * time will be recorded and any attempt to authenticated a locked user will log a warning.
     */
    public boolean isLocked(String username) {
        LockRecord lockRecord = null;
        synchronized (this) {
            lockRecord = failedUsers.get(username);
        }

        // No lock record means user can't be locked
        if (lockRecord == null) {
            return false;
        }

        // Check to see if user is locked
        if (lockRecord.getFailures() >= failureCount &&
                (System.currentTimeMillis() - lockRecord.getLastFailureTime()) / 1000 < lockOutTime) {
            return true;
        }

        // User has not, yet, exceeded lock thresholds
        return false;
    }


    /*
     * After successful authentication, any record of previous authentication failure is removed.
     */
    private synchronized void registerAuthSuccess(String username) {
        // Successful authentication means removal from the list of failed users
        failedUsers.remove(username);
    }


    /*
     * After a failed authentication, add the record of the failed authentication.
     */
    private void registerAuthFailure(String username) {
        LockRecord lockRecord = null;
        synchronized (this) {
            if (!failedUsers.containsKey(username)) {
                lockRecord = new LockRecord();
                failedUsers.put(username, lockRecord);
            } else {
                lockRecord = failedUsers.get(username);
                if (lockRecord.getFailures() >= failureCount &&
                        ((System.currentTimeMillis() - lockRecord.getLastFailureTime()) / 1000) > lockOutTime) {
                    // User was previously locked out but lockout has now
                    // expired so reset failure count
                    lockRecord.setFailures(0);
                }
            }
        }
        lockRecord.registerFailure();
    }


    /**
     * Get the number of failed authentication attempts required to lock the user account.
     *
     * @return the failureCount
     */
    public int getFailureCount() {
        return failureCount;
    }


    /**
     * Set the number of failed authentication attempts required to lock the user account.
     *
     * @param failureCount the failureCount to set
     */
    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }


    /**
     * Get the period for which an account will be locked.
     *
     * @return the lockOutTime
     */
    public int getLockOutTime() {
        return lockOutTime;
    }


    /**
     * Set the period for which an account will be locked.
     *
     * @param lockOutTime the lockOutTime to set
     */
    public void setLockOutTime(int lockOutTime) {
        this.lockOutTime = lockOutTime;
    }


    /**
     * Get the maximum number of users for which authentication failure will be kept in the cache.
     *
     * @return the cacheSize
     */
    public int getCacheSize() {
        return cacheSize;
    }


    /**
     * Set the maximum number of users for which authentication failure will be kept in the cache.
     *
     * @param cacheSize the cacheSize to set
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }


    /**
     * Get the minimum period a failed authentication must remain in the cache to avoid generating a warning if it is
     * removed from the cache to make space for a new entry.
     *
     * @return the cacheRemovalWarningTime
     */
    public int getCacheRemovalWarningTime() {
        return cacheRemovalWarningTime;
    }


    /**
     * Set the minimum period a failed authentication must remain in the cache to avoid generating a warning if it is
     * removed from the cache to make space for a new entry.
     *
     * @param cacheRemovalWarningTime the cacheRemovalWarningTime to set
     */
    public void setCacheRemovalWarningTime(int cacheRemovalWarningTime) {
        this.cacheRemovalWarningTime = cacheRemovalWarningTime;
    }


    protected static class LockRecord {
        private final AtomicInteger failures = new AtomicInteger(0);
        private long lastFailureTime = 0;

        public int getFailures() {
            return failures.get();
        }

        public void setFailures(int theFailures) {
            failures.set(theFailures);
        }

        public long getLastFailureTime() {
            return lastFailureTime;
        }

        public void registerFailure() {
            failures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }
    }
}
