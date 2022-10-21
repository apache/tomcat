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
package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.PooledConnection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestValidation extends DefaultTestCase {

    public static final Boolean WITHAUTOCOMMIT = Boolean.TRUE;
    public static final Boolean NOAUTOCOMMIT = Boolean.FALSE;

    public static final String WITHVALIDATIONQUERY = "SELECT 1";
    public static final String NOVALIDATIONQUERY = null;

    @Before
    public void setUp() throws SQLException {
        DriverManager.registerDriver(new MockDriver());

        // use our mock driver
        datasource.setDriverClassName(MockDriver.class.getName());
        datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(ValidationOutcome.SUCCESS));

        // Required to trigger validation query's execution
        datasource.setInitialSize(1);
        datasource.setMinIdle(1);
        datasource.setMaxIdle(1);
        datasource.setMaxActive(2);
        // Validation interval is disabled by default to ensure validation occurs every time
        datasource.setValidationInterval(-1);
        // No validation query by default
        datasource.setValidationQuery(null);
    }

    @After
    public void cleanup() throws SQLException {
        datasource = createDefaultDataSource();
        DriverManager.deregisterDriver(new MockDriver());
    }

    private PooledConnection getPooledConnection() throws SQLException {
        return (PooledConnection) datasource.getConnection();
    }

    private static MockConnection getMock(PooledConnection pooledConnection) throws SQLException {
        return (MockConnection) pooledConnection.getConnection();
    }

    /* -------------------------------- *
     * Validation onConnect             *
     * -------------------------------- */

    private void checkOnConnectValidationWithOutcome(ValidationOutcome validationOutcomes, String validationQuery, Boolean autoCommit) throws SQLException {
        datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(validationOutcomes));
        datasource.getPoolProperties().setTestOnConnect(true);
        datasource.getPoolProperties().setValidationQuery(validationQuery);
        datasource.getPoolProperties().setDefaultAutoCommit(autoCommit);
        PooledConnection cxn1 = getPooledConnection();
        MockConnection mockCxn1 = getMock(cxn1);
        Assert.assertFalse("No transaction must be running after connection is obtained", mockCxn1.isRunningTransaction());
    }

    /* ------- No validation query ----- */

    @Test
    public void testOnConnectValidationWithoutValidationQueryDoesNotOccurWhenDisabled() throws SQLException {
        datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(ValidationOutcome.FAILURE));
        datasource.getPoolProperties().setTestOnConnect(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnConnectValidationSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectFailureThenSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectExceptionThenSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnConnectValidationSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectFailureThenSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectExceptionThenSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* ------- With validation query ----- */

    @Test
    public void testOnConnectValidationWithValidationSQLDoesNotOccurWhenDisabled() throws SQLException {
        this.datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(ValidationOutcome.FAILURE));
        datasource.getPoolProperties().setTestOnConnect(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        datasource.getPoolProperties().setValidationQuery("SELECT 1");
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnConnectValidationSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectFailureThenSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectExceptionThenSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnConnectValidationSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectFailureThenSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test(expected=SQLException.class)
    public void testOnConnectExceptionThenSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        checkOnConnectValidationWithOutcome(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* -------------------------------- *
     * Validation onBorrow              *
     * -------------------------------- */

    private void obtainCxnWithValidationOutcomeAndAttemptAgain(ValidationOutcome validationOutcome, String validationQuery, Boolean autoCommit) throws SQLException {
        datasource.getPoolProperties().setValidationQuery(validationQuery);
        datasource.getPoolProperties().setDefaultAutoCommit(autoCommit);

        PooledConnection cxn1 = getPooledConnection();
        MockConnection mockCxn1 = getMock(cxn1);
        Assert.assertFalse("No transaction must be running after connection is obtained", mockCxn1.isRunningTransaction());

        // Discard connection and set next validation outcome to provided outcome value
        mockCxn1.setValidationOutcome(validationOutcome);
        cxn1.close();

        PooledConnection cxn2 = getPooledConnection();
        MockConnection mockCxn2 = getMock(cxn2);
        Assert.assertFalse("No transaction must be running after connection is obtained", mockCxn2.isRunningTransaction());
        if (validationOutcome == ValidationOutcome.SUCCESS) {
            Assert.assertEquals("Connection with successful validation is reused", mockCxn1, mockCxn2);
        } else {
            Assert.assertNotEquals("Connection with failed validation must not be returned again", mockCxn1, mockCxn2);
        }
    }

    private void obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome validationOutcome, String validationQuery, Boolean autoCommit) throws SQLException {
        datasource.getPoolProperties().setTestOnBorrow(true);
        obtainCxnWithValidationOutcomeAndAttemptAgain(validationOutcome, validationQuery, autoCommit);
    }

    /* ------- No validation query ----- */

    @Test
    public void testOnBorrowValidationWithoutValidationQueryDoesNotOccurWhenDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnBorrow(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnBorrowValidationSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationFailureWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationExceptionWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationFailureWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationExceptionWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* ------- With validation query ----- */

    @Test
    public void testOnBorrowValidationWithValidationQueryDoesNotOccurWhenDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnBorrow(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        datasource.getPoolProperties().setValidationQuery("SELECT 1");
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnBorrowValidationSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationFailureWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationExceptionWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationFailureWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnBorrowValidationExceptionWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnBorrowValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* -------------------------------- *
     * Validation onReturn              *
     * -------------------------------- */

    private void obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome validationOutcome, String validationQuery, Boolean autoCommit) throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(true);
        obtainCxnWithValidationOutcomeAndAttemptAgain(validationOutcome, validationQuery, autoCommit);
    }

    /* ------- No validation query ----- */

    @Test
    public void testOnReturnValidationWithoutValidationQueryDoesNotOccurWhenDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnReturnValidationSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationFailureWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationExceptionWithoutValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(true);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationFailureWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(true);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationExceptionWithoutValidationQueryAndAutoCommitDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(true);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* ------- With validation query ----- */

    @Test
    public void testOnReturnValidationWithValidationQueryDoesNotOccurWhenDisabled() throws SQLException {
        datasource.getPoolProperties().setTestOnReturn(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        datasource.getPoolProperties().setValidationQuery("SELECT 1");
        PooledConnection cxn = getPooledConnection();
        Assert.assertFalse("No transaction must be running after connection is obtained", getMock(cxn).isRunningTransaction());
    }

    @Test
    public void testOnReturnValidationSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationFailureWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationExceptionWithValidationQueryAndAutoCommitEnabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationFailureWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testOnReturnValidationExceptionWithValidationQueryAndAutoCommitDisabled() throws SQLException {
        obtainCxnWithOnReturnValidationOutcomeAndAttemptAgain(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* -------------------------------- *
     * Validation whileIdle              *
     * -------------------------------- */

    private void obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome validationOutcome, String validationQuery, Boolean autoCommit)
            throws SQLException, InterruptedException {
        datasource.getPoolProperties().setTestWhileIdle(true);
        datasource.getPoolProperties().setValidationInterval(1);
        datasource.getPoolProperties().setDefaultAutoCommit(autoCommit);
        datasource.getPoolProperties().setValidationQuery(validationQuery);
        datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(validationOutcome));

        PooledConnection cxn1 = getPooledConnection();
        MockConnection mockCxn1 = getMock(cxn1);
        Assert.assertFalse("No transaction must be running after connection is obtained", mockCxn1.isRunningTransaction());

        cxn1.close();
        Assert.assertEquals("Pool must contain 1 idle connection at this time", datasource.getIdle(), 1);

        Thread.sleep(1200); // Nasty - instrument PooledConnection to drive time measurement instead of hard-coded System.currentTimeMillis()
        datasource.testIdle();

        if (validationOutcome == ValidationOutcome.SUCCESS) {
            Assert.assertEquals("Pool must contain 1 idle connection at this time", datasource.getIdle(), 1);
        } else {
            Assert.assertEquals("Pool must not contain any idle connection at this time", datasource.getIdle(), 0);
        }
    }

    /* ------- No validation query ----- */

    @Test
    public void testWhileIdleReturnValidationWithoutValidationQueryDoesNotOccurWhenDisabled() throws SQLException, InterruptedException {
        datasource.setUrl(MockDriver.getUrlWithValidationOutcomes(ValidationOutcome.FAILURE));
        datasource.getPoolProperties().setTestWhileIdle(false);
        datasource.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        datasource.getPoolProperties().setValidationInterval(1);

        PooledConnection cxn = getPooledConnection();
        cxn.close();
        Assert.assertEquals("Pool must contain 1 idle connection at this time", datasource.getIdle(), 1);

        Thread.sleep(1200); // Nasty - instrument PooledConnection to drive time measurement instead of hard-coded System.currentTimeMillis()
        datasource.testIdle();
        Assert.assertEquals("Pool must contain 1 idle connection at this time", datasource.getIdle(), 1);
    }

    @Test
    public void testWhileIdleValidationSuccessWithoutValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationFailureWithoutValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationExceptionWithoutValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationSuccessWithoutValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.SUCCESS, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationFailureWithoutValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.FAILURE, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationExceptionWithoutValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.EXCEPTION, NOVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* ------- With validation query ----- */

    @Test
    public void testWhileIdleValidationSuccessWithValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationFailureWithValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationExceptionWithValidationQueryAndAutoCommitEnabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, WITHAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationSuccessWithValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.SUCCESS, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationFailureWithValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.FAILURE, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    @Test
    public void testWhileIdleValidationExceptionWithValidationQueryAndAutoCommitDisabled() throws SQLException, InterruptedException {
        obtainThenReleaseCxnAndAssessIdleValidationWithOutcome(ValidationOutcome.EXCEPTION, WITHVALIDATIONQUERY, NOAUTOCOMMIT);
    }

    /* ------- Helper mock classes ----- */

    public static enum ValidationOutcome {
        SUCCESS,  // Validation returns true
        FAILURE,  // Validation returns false
        EXCEPTION // Validation throws an unexpected exception
    }

    /**
     * Mock Driver, Connection and Statement implementations used to control validation outcome and verify transaction status.
     */
    public static class MockDriver implements java.sql.Driver {
        public static final String url = "jdbc:tomcat:mock";

        public static String getUrlWithValidationOutcomes(ValidationOutcome validationOutcome) {
            return url + "?" + validationOutcome;
        }

        private ValidationOutcome getValidationOutcomeFromUrl(String url) {
            String outcomesAsString = url.substring(url.lastIndexOf("?")+1);
            return ValidationOutcome.valueOf(outcomesAsString);
        }

        public MockDriver() {
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url!=null && url.startsWith(MockDriver.url);
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return new MockConnection(info, getValidationOutcomeFromUrl(url));
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    public static class MockConnection extends org.apache.tomcat.jdbc.test.driver.Connection {
        private boolean autoCommit = false;
        private boolean runningTransaction = false;
        private ValidationOutcome validationOutcome;

        public MockConnection(Properties info, ValidationOutcome validationOutcome) {
            super(info);
            this.validationOutcome = validationOutcome;
        }

        public boolean isRunningTransaction() {
            return runningTransaction;
        }

        protected void statementExecuted() {
            this.runningTransaction = !autoCommit;
        }

        protected void transactionCleared() {
            this.runningTransaction = false;
        }

        protected void setValidationOutcome(ValidationOutcome validationOutcome) {
            this.validationOutcome = validationOutcome;
        }

        protected ValidationOutcome getValidationOutcome() {
            return validationOutcome;
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return autoCommit;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            this.autoCommit = autoCommit;
        }

        @Override
        public Statement createStatement() throws SQLException {
            return new MockStatement(this);
        }

        @Override
        public void commit() throws SQLException {
            super.commit();
            transactionCleared();
        }

        @Override
        public void rollback() throws SQLException {
            super.rollback();
            transactionCleared();
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            super.rollback(savepoint);
            transactionCleared();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            statementExecuted();
            switch (validationOutcome) {
            case SUCCESS: { return true; }
            case FAILURE: { return false; }
            case EXCEPTION: { throw new SQLException("Unexpected error generated in test"); }
            default: { return true; }
            }
        }
    }

    public static class MockStatement extends org.apache.tomcat.jdbc.test.driver.Statement {

        private MockConnection connection;

        public MockStatement(MockConnection connection) {
            super();
            this.connection = connection;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            if (connection.getValidationOutcome()==ValidationOutcome.SUCCESS) {
                return false;
            } else {
                throw new SQLException("Simulated validation query failure");
            }
        }
    }

}