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
package org.apache.tomcat.dbcp.dbcp2;

import org.easymock.EasyMock;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

public class TestDbcpRequestBoundaries {

    Driver driver = EasyMock.createNiceMock(Driver.class);

    @Test
    public void testBeginRequestOneConnection() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, true);
        Connection connection = setupPhysicalConnection(1, 0);
        EasyMock.replay(driver, connection);

        // Get connection
        dataSource.getConnection();

        // Verify number of calls
        EasyMock.verify(connection);
    }

    @Test
    public void testEndRequestOneConnection() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, true);
        Connection connection = setupPhysicalConnection(1, 1);
        EasyMock.replay(driver, connection);

        // Get then close connection
        dataSource.getConnection().close();

        // Verify number of calls
        EasyMock.verify(connection);
    }

    @Test
    public void testBeginRequestTwoVirtualConnections() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, true);
        Connection connection = setupPhysicalConnection(2, 1);
        EasyMock.replay(driver, connection);

        // Get connection close it then get another connection
        dataSource.getConnection().close();
        dataSource.getConnection();

        // Verify number calls
        EasyMock.verify(connection);
    }

    @Test
    public void testEndRequestTwoVirtualConnections() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, true);
        Connection connection = setupPhysicalConnection(2, 2);
        EasyMock.replay(driver, connection);

        // Get a connection and close then get another connection and close it
        dataSource.getConnection().close();
        dataSource.getConnection().close();

        // Verify number of calls
        EasyMock.verify(connection);
    }

    @Test
    public void testRequestBoundariesTwoPhysicalConnections() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion >= 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, true);
        Connection connection1 = setupPhysicalConnection(1, 1);
        Connection connection2 = setupPhysicalConnection(1, 0);
        EasyMock.replay(driver, connection1, connection2);

        // Get a connection, then get another connection, then close the first connection
        Connection fetchedConnection = dataSource.getConnection();
        dataSource.getConnection();
        fetchedConnection.close();

        // Verify number of calls
        EasyMock.verify(connection1, connection2);
    }

    @Test
    public void testConnectionWithoutRequestBoundaries() throws SQLException {
        // Verify JDK version
        double javaVersion = Double.valueOf(System.getProperty("java.class.version"));
        org.junit.Assume.assumeTrue(javaVersion < 53);

        // Setup
        BasicDataSource dataSource = getDataSource(driver, false);
        Connection connection = setupPhysicalConnection(0, 0);
        EasyMock.replay(driver, connection);

        // Get connection
        dataSource.getConnection().close();

        // Verify that no unexpected calls where made
        EasyMock.verifyUnexpectedCalls(connection);
    }

    public BasicDataSource getDataSource(Driver driver, boolean validateConnectionFactory) throws SQLException {
        EasyMock.reset(driver);

        BasicDataSource dataSource = BasicDataSourceFactory.createDataSource(new Properties());
        dataSource.setDriver(driver);

        // Before testing the call count of beginRequest and endRequest method we'll make sure that the
        // connectionFactory has been validated which involves creating a physical connection and destroying it. If we
        // don't, it's going to be done automatically when the first connection is requested. This is going to mess the
        // call count.
        if (validateConnectionFactory) {validateConnectionFactory(dataSource, driver);}

        return dataSource;
    }

    public void validateConnectionFactory(BasicDataSource dataSource, Driver driver) throws SQLException {
        Connection connection = getmockedConnection();

        EasyMock.expect(driver.connect(EasyMock.isNull(), EasyMock.anyObject(Properties.class)))
            .andReturn(connection)
            .anyTimes();

        connection.beginRequest();
        EasyMock.expectLastCall().anyTimes();
        connection.endRequest();
        EasyMock.expectLastCall().anyTimes();

        EasyMock.replay(driver, connection);
        dataSource.getLogWriter();

        EasyMock.reset(driver);
    }

    public Connection getmockedConnection() throws SQLException {
        Connection connection = EasyMock.createMock(Connection.class);

        EasyMock.expect(connection.isClosed()).andStubReturn(false);
        EasyMock.expect(connection.isValid(EasyMock.anyInt())).andStubReturn(true);
        EasyMock.expect(connection.getAutoCommit()).andStubReturn(true);

        connection.clearWarnings();
        EasyMock.expectLastCall().anyTimes();
        connection.close();
        EasyMock.expectLastCall().anyTimes();

        return connection;
    }

    public Connection setupPhysicalConnection(int beginRequestExpectedCalls, int endRequestExpectedCalls)
        throws SQLException {
        Connection connection = getmockedConnection();
        EasyMock.expect(driver.connect(EasyMock.isNull(), EasyMock.anyObject(Properties.class))).andReturn(connection);

        // Expected number of calls
        expectBeginRequestCallOnConnection(connection, beginRequestExpectedCalls);
        expectEndRequestCallOnConnection(connection, endRequestExpectedCalls);

        return connection;
    }

    public void expectBeginRequestCallOnConnection(Connection connection, int numberOfCalls) throws SQLException {
        if (numberOfCalls == 0) {
            // The verification of unexpected calls is done implicitly
            return;
        }
        connection.beginRequest();
        EasyMock.expectLastCall().times(numberOfCalls);
    }

    public void expectEndRequestCallOnConnection(Connection connection, int numberOfCalls) throws SQLException {
        if (numberOfCalls == 0) {
            // The verification of unexpected calls is done implicitly
            return;
        }
        connection.endRequest();
        EasyMock.expectLastCall().times(numberOfCalls);
    }
}
