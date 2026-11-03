/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A returned handle is a pool borrow, not a physical close, and the pool's
 * return-time transaction termination unwinds only local work: it can never
 * resolve an XA branch. A handle closed between {@code xa.end} and
 * {@code xa.commit} therefore used to re-pool the session with the branch -
 * and every database lock held for it - attached, so the next borrower
 * inherited them and the transaction manager's late commit landed on a
 * session that does not carry the branch. The pool must instead take such a
 * session out of service, so the resource manager drops the branch with the
 * session and the late commit fails visibly instead of silently misfiring
 * across borrowers.
 */
class TestUnresolvedXaBranchOnReturn extends JdbcPoolBaseTest {

    /** Simple test transaction id. */
    private static final class TestXid implements Xid {
        private final byte qualifier;
        TestXid(int qualifier) {
            this.qualifier = (byte) qualifier;
        }
        @Override
        public int getFormatId() {
            return 1;
        }
        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] { 42, qualifier };
        }
        @Override
        public byte[] getBranchQualifier() {
            return new byte[] { qualifier };
        }
    }

    private XADataSource newXaPool() {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        XADataSource ds = new XADataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(inner);
        return manage(ds);
    }

    @Test
    void aPreparedBranchOutlivingTheHandleKeepsTheSessionOutOfThePool() throws Exception {
        XADataSource ds = newXaPool();
        String t = uniqueTable();
        // The table itself is committed work, so the branch's row stays
        // countable after the branch - and with it the session - is retired.
        try (Connection setup = ds.getConnection();
                Statement st = setup.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }

        XAConnection xc = ds.getXAConnection();
        XAResource xa = xc.getXAResource();
        Assertions.assertSame(xa, xc.getXAResource(),
                "repeated calls must name the same XA resource");
        Connection c = xc.getConnection();
        c.setAutoCommit(false);
        Xid xid = new TestXid(11);

        xa.start(xid, XAResource.TMNOFLAGS);
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO " + t + " VALUES (1)");
        }
        setSessionMarker(c, 77);
        xa.end(xid, XAResource.TMSUCCESS);
        Assertions.assertEquals(XAResource.XA_OK, xa.prepare(xid));
        Assertions.assertEquals(0, adminRowCount(t), "work must be invisible before commit");

        // The spec-shaped misuse: abandon the handle between end/prepare and
        // commit. A physical close would have dropped the branch; the pool
        // must not turn that close into a re-pool.
        xc.close();

        Assertions.assertEquals(0, poolSessions(),
                "a session carrying an unresolved prepared branch must not stay in the pool");

        // The late commit of an abandoned branch must fail visibly against
        // the retired session, not quietly succeed on a re-pooled one - and
        // as a refusal of the resource manager, the code the XA
        // specification gives for a resource that can no longer serve.
        XAException refused = Assertions.assertThrows(XAException.class, () -> xa.commit(xid, false),
                "committing a branch whose session the pool retired must fail");
        Assertions.assertEquals(XAException.XAER_RMFAIL, refused.errorCode);
        Assertions.assertEquals(0, adminRowCount(t),
                "the abandoned branch must not resurface through another borrower");

        try (Connection again = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(again),
                    "the next borrower must not inherit the session the branch was parked on");
        }
    }

    @Test
    void anOpenBranchOutlivingTheHandleKeepsTheSessionOutOfThePool() throws Exception {
        XADataSource ds = newXaPool();
        String t = uniqueTable();
        try (Connection setup = ds.getConnection();
                Statement st = setup.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }

        XAConnection xc = ds.getXAConnection();
        XAResource xa = xc.getXAResource();
        Connection c = xc.getConnection();
        c.setAutoCommit(false);
        Xid xid = new TestXid(12);

        // A branch that stands started and never resolved is just as attached
        // to the session as a prepared one: end() alone would not detach it
        // either, and the locks its work took are held the same way.
        xa.start(xid, XAResource.TMNOFLAGS);
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO " + t + " VALUES (1)");
        }
        setSessionMarker(c, 78);

        xc.close();

        Assertions.assertEquals(0, poolSessions(),
                "a session carrying an unresolved branch must not stay in the pool");
        XAException refused = Assertions.assertThrows(XAException.class, () -> xa.rollback(xid),
                "resolving a branch whose session the pool retired must fail");
        Assertions.assertEquals(XAException.XAER_RMFAIL, refused.errorCode);
        Assertions.assertEquals(0, adminRowCount(t),
                "the abandoned branch's work must not resurface through another borrower");

        try (Connection again = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(again),
                    "the next borrower must not inherit the session the branch was parked on");
        }
    }
}
