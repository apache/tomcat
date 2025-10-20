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
package org.apache.catalina.servlets;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.servlets.WebdavServlet.PropertyStore;
import org.apache.catalina.servlets.WebdavServlet.PropertyUpdateType;
import org.apache.catalina.servlets.WebdavServlet.ProppatchOperation;
import org.apache.catalina.startup.LoggingBaseTest;
import org.apache.catalina.util.XMLWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@RunWith(Parameterized.class)
public class TestWebdavPropertyStore extends LoggingBaseTest {

    private static final String PROPERTY1 =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<T:customprop xmlns:T=\"http://tomcat.apache.org/testsuite\">\n" +
            "  <T:myvalue/>\n" +
            "</T:customprop>";

    private static final String PROPERTY2 =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<V:someprop xmlns:V=\"http://tomcat.apache.org/other\">\n" +
            "  <V:myvalue>bla</V:myvalue>\n" +
            "</V:someprop>";

    private static final String PROPERTY3 =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<V:someprop xmlns:V=\"http://tomcat.apache.org/other\">\n" +
            "  <V:othervalue>foooooooo</V:othervalue>\n" +
            "</V:someprop>";

    public static final String SIMPLE_SCHEMA =
            "CREATE TABLE webdavproperties (\n" +
            "  path         VARCHAR(1024) NOT NULL,\n" +
            "  namespace    VARCHAR(64) NOT NULL,\n" +
            "  name         VARCHAR(64) NOT NULL,\n" +
            "  node         VARCHAR(2048) NOT NULL,\n" +
            "  PRIMARY KEY (path, namespace, name)\n" +
            ")";

    public static class CustomDataSourcePropertyStore extends DataSourcePropertyStore {
        public void setDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }
    }

    private class DerbyDataSource implements DataSource {

        Connection connection = null;

        DerbyDataSource() {
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                connection = DriverManager.getConnection("jdbc:derby:" + getTemporaryDirectory().getAbsolutePath()
                        + "/webdavproperties;create=true");
                try (Statement statement = connection.createStatement()) {
                    statement.execute(SIMPLE_SCHEMA);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:derby:" + getTemporaryDirectory().getAbsolutePath()
                        + "/webdavproperties");
            }
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] { "org.apache.catalina.servlets.WebdavServlet$MemoryPropertyStore" });
        parameterSets.add(new Object[] { "org.apache.catalina.servlets.TestWebdavPropertyStore$CustomDataSourcePropertyStore" });
        return parameterSets;
    }

    @Parameter(0)
    public String storeName;

    @Test
    public void testStore() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setExpandEntityReferences(false);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document1 = documentBuilder.parse(new InputSource(new ByteArrayInputStream(PROPERTY1.getBytes(StandardCharsets.UTF_8))));
        Node node1 = document1.getDocumentElement();
        Document document2 = documentBuilder.parse(new InputSource(new ByteArrayInputStream(PROPERTY2.getBytes(StandardCharsets.UTF_8))));
        Node node2 = document2.getDocumentElement();
        Document document3 = documentBuilder.parse(new InputSource(new ByteArrayInputStream(PROPERTY3.getBytes(StandardCharsets.UTF_8))));
        Node node3 = document3.getDocumentElement();

        PropertyStore propertyStore = (PropertyStore) Class.forName(storeName).getDeclaredConstructor().newInstance();
        if (propertyStore instanceof CustomDataSourcePropertyStore) {
            ((CustomDataSourcePropertyStore) propertyStore).setDataSource(new DerbyDataSource());
            ((CustomDataSourcePropertyStore) propertyStore).setTableName("webdavproperties");
        }
        propertyStore.init();

        // Add properties
        ArrayList<ProppatchOperation> operations = new ArrayList<>();
        operations.add(new ProppatchOperation(PropertyUpdateType.SET, node1));
        operations.add(new ProppatchOperation(PropertyUpdateType.SET, node2));
        propertyStore.proppatch("/some/path1", operations);

        // Add properties
        operations = new ArrayList<>();
        operations.add(new ProppatchOperation(PropertyUpdateType.SET, node1));
        propertyStore.proppatch("/other/path2", operations);

        // Get single property
        XMLWriter xmlWriter1 = new XMLWriter();
        Assert.assertTrue(propertyStore.propfind("/some/path1", node1, false, xmlWriter1));
        Assert.assertTrue(xmlWriter1.toString().contains("<myvalue "));

        // Get property names
        XMLWriter xmlWriter2 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/some/path1", null, true, xmlWriter2));
        Assert.assertTrue(xmlWriter2.toString().contains("<someprop"));

        // Get all properties
        XMLWriter xmlWriter3 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/some/path1", null, false, xmlWriter3));
        Assert.assertTrue(xmlWriter3.toString().contains(">bla</myvalue>"));

        propertyStore.copy("/some/path1", "/some/path2");
        XMLWriter xmlWriter4 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/some/path2", null, true, xmlWriter4));
        Assert.assertTrue(xmlWriter4.toString().contains("<someprop"));

        propertyStore.delete("/some/path1");
        XMLWriter xmlWriter5 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/some/path1", null, true, xmlWriter5));
        Assert.assertTrue(xmlWriter5.toString().isEmpty());

        propertyStore.copy("/some/path2", "/other/path2");
        XMLWriter xmlWriter6 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/other/path2", null, true, xmlWriter6));
        Assert.assertTrue(xmlWriter6.toString().contains("<customprop"));

        operations = new ArrayList<>();
        operations.add(new ProppatchOperation(PropertyUpdateType.REMOVE, node1));
        propertyStore.proppatch("/other/path2", operations);

        XMLWriter xmlWriter7 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/other/path2", null, false, xmlWriter7));
        Assert.assertFalse(xmlWriter7.toString().contains("<customprop"));
        Assert.assertTrue(xmlWriter7.toString().contains(">bla</myvalue>"));

        operations = new ArrayList<>();
        operations.add(new ProppatchOperation(PropertyUpdateType.SET, node3));
        propertyStore.proppatch("/other/path2", operations);

        XMLWriter xmlWriter8 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/other/path2", null, false, xmlWriter8));
        Assert.assertFalse(xmlWriter8.toString().contains("<customprop"));
        Assert.assertTrue(xmlWriter8.toString().contains(">foooooooo</othervalue>"));

        XMLWriter xmlWriter9 = new XMLWriter();
        Assert.assertFalse(propertyStore.propfind("/other/path2", node1, false, xmlWriter9));
        Assert.assertTrue(xmlWriter9.toString().isEmpty());

        propertyStore.destroy();

    }
}
