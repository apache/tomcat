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
package org.apache.tomcat.jdbc.pool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * The pool's XA connection wrapper. It hands the driver's handle out to the
 * pool and to borrowers unchanged in every call - it only delegates - and
 * books, next to it, the transaction branches that were started through this
 * session's XA resource and never resolved: committed, rolled back or
 * forgotten.
 * <p>
 * The bookkeeping exists because a borrow's {@code close()} recycles the
 * session instead of closing it, while the return-time transaction
 * termination unwinds only local work and can never resolve an XA branch. A
 * session that a borrower abandons mid-branch would otherwise go back into
 * the idle queue carrying the branch and every database lock held for it,
 * and the transaction manager's late commit would land on a session that
 * does not carry it. The pool consults {@link #hasUnresolvedBranches()} when
 * a connection is returned and retires such a session instead of pooling it.
 */
final class XABranchTracker implements XAConnection {

    /**
     * The identity a branch is booked and resolved under. Xid implementations
     * compare by value per the XA specification, but nothing forces a
     * transaction manager's Xid to honour that, and the pool must not mistake
     * a re-created equal branch for a new one: the key snapshots the content
     * itself, and with it also survives a caller mutating its arrays.
     */
    private static final class XidKey {
        private final int formatId;
        private final byte[] globalId;
        private final byte[] branchId;
        private final int hash;

        XidKey(Xid xid) {
            formatId = xid.getFormatId();
            byte[] gtid = xid.getGlobalTransactionId();
            globalId = gtid == null ? new byte[0] : gtid.clone();
            byte[] bqual = xid.getBranchQualifier();
            branchId = bqual == null ? new byte[0] : bqual.clone();
            hash = (Arrays.hashCode(globalId) * 31 + formatId) * 31 + Arrays.hashCode(branchId);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof XidKey)) {
                return false;
            }
            XidKey other = (XidKey) obj;
            return formatId == other.formatId
                    && Arrays.equals(globalId, other.globalId)
                    && Arrays.equals(branchId, other.branchId);
        }
    }

    private final XAConnection delegate;

    /**
     * Branches started on this session that the resource manager still
     * considers attached to it. A branch is booked before its {@code start()}
     * reaches the driver, so a concurrent return sees it as soon as the driver
     * can have accepted it, and a start the driver refuses removes a key it
     * newly booked. {@code end()} does not detach a branch - only commit,
     * rollback or a successful forget does, and neither does a refusal of any
     * of them: a branch whose resolution failed stays booked, because whether
     * the resource manager kept it is then unknown and retiring the session is
     * the only safe answer.
     */
    private final Set<XidKey> unresolved = ConcurrentHashMap.newKeySet();

    /**
     * The one tracking resource this handle owns, so that repeated
     * {@code getXAResource()} calls name the same object the way the driver's
     * own handle does, and the bookkeeping cannot depend on which of the
     * objects a transaction manager happened to keep.
     */
    private volatile XAResource trackedResource;

    /**
     * Set when the pool retires the session behind this handle. Branch
     * lifecycle calls on a resource captured from the handle are then refused
     * right here instead of reaching the closed driver object: a discarded
     * session is terminal, its branches dropped with it, and a driver that
     * does not fail cleanly on its own closed handle would otherwise surface
     * the misuse as an unrelated runtime failure at the transaction manager.
     */
    private volatile boolean closed;

    XABranchTracker(XAConnection delegate) {
        this.delegate = delegate;
    }

    /**
     * Marks the session behind this handle as retired, from the pool's
     * disconnect path. After this, no branch work is accepted any more.
     */
    synchronized void markClosed() {
        closed = true;
    }

    /**
     * Reports whether any branch started through this session is still
     * unresolved.
     *
     * @return true if a branch was started and never committed, rolled back
     *             or forgotten on this session
     */
    boolean hasUnresolvedBranches() {
        return !unresolved.isEmpty();
    }

    // javax.sql.PooledConnection / javax.sql.XAConnection

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }

    @Override
    public XAResource getXAResource() throws SQLException {
        XAResource resource = trackedResource;
        if (resource == null) {
            synchronized (this) {
                resource = trackedResource;
                if (resource == null) {
                    resource = new TrackedResource(delegate.getXAResource());
                    trackedResource = resource;
                }
            }
        }
        return resource;
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        delegate.addConnectionEventListener(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        delegate.removeConnectionEventListener(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        delegate.addStatementEventListener(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        delegate.removeStatementEventListener(listener);
    }

    /**
     * The tracked XA resource. Every call is dispatched to the driver's
     * resource exactly as it is; the wrapper only books the branch lifecycle
     * beside it.
     */
    private final class TrackedResource implements XAResource {

        private final XAResource delegate;

        TrackedResource(XAResource delegate) {
            this.delegate = delegate;
        }

        /**
         * Branch work on a retired session cannot succeed: the session is
         * closed, its unresolved branches were dropped with it, and none of
         * these calls reaches anything any more. The refusal names the
         * resource manager unavailable, the code the XA specification gives
         * for a resource that cannot serve the call, so the transaction
         * manager sees a clean XA failure instead of whatever the closed
         * driver object happens to raise.
         */
        private void refuseRetiredSession() throws XAException {
            if (closed) {
                XAException failure = new XAException(
                        MessageFactory.get("xaBranchTracker.resourceRetired"));
                failure.errorCode = XAException.XAER_RMFAIL;
                throw failure;
            }
        }

        @Override
        public void start(Xid xid, int flags) throws XAException {
            XidKey key = new XidKey(xid);
            // Book the branch before handing the call to the driver. A return or abandon running concurrently must
            // see the branch as soon as the driver can have accepted it; booking only after start() returned left a
            // window in which the session could be pooled with the accepted branch attached. A failed start removes a
            // newly booked key again; a key that was already present (a join or resume of a live branch) stays
            // booked, since its branch was not created by this call.
            refuseRetiredSession();
            boolean added = unresolved.add(key);
            try {
                delegate.start(xid, flags);
            } catch (XAException x) {
                if (added) {
                    unresolved.remove(key);
                }
                throw x;
            }
        }

        @Override
        public void end(Xid xid, int flags) throws XAException {
            refuseRetiredSession();
            delegate.end(xid, flags);
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            refuseRetiredSession();
            int outcome = delegate.prepare(xid);
            if (outcome == XAResource.XA_RDONLY) {
                // The resource manager resolved the branch itself: a
                // read-only branch is complete at prepare and no commit will
                // ever come for it.
                unresolved.remove(new XidKey(xid));
            }
            return outcome;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) throws XAException {
            refuseRetiredSession();
            delegate.commit(xid, onePhase);
            unresolved.remove(new XidKey(xid));
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            refuseRetiredSession();
            delegate.rollback(xid);
            unresolved.remove(new XidKey(xid));
        }

        @Override
        public void forget(Xid xid) throws XAException {
            refuseRetiredSession();
            delegate.forget(xid);
            unresolved.remove(new XidKey(xid));
        }

        @Override
        public Xid[] recover(int flag) throws XAException {
            refuseRetiredSession();
            return delegate.recover(flag);
        }

        @Override
        public boolean setTransactionTimeout(int seconds) throws XAException {
            refuseRetiredSession();
            return delegate.setTransactionTimeout(seconds);
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            refuseRetiredSession();
            return delegate.getTransactionTimeout();
        }

        @Override
        public boolean isSameRM(XAResource xares) throws XAException {
            // Two tracked resources are of the same resource manager exactly
            // when the resources underneath them are; an unwrapped resource
            // is compared against the driver's own object directly.
            if (xares instanceof TrackedResource) {
                return delegate.isSameRM(((TrackedResource) xares).delegate);
            }
            return delegate.isSameRM(xares);
        }
    }
}
