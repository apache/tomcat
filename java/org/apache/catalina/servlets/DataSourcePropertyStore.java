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

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.servlets.WebdavServlet.PropertyUpdateType;
import org.apache.catalina.servlets.WebdavServlet.ProppatchOperation;
import org.apache.catalina.util.DOMWriter;
import org.apache.catalina.util.XMLWriter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.w3c.dom.Node;

/**
 * WebDAV dead properties storage using a DataSource.
 * <p>
 * A single properties table with four columns is used:
 * <ul>
 * <li>path: the resource path</li>
 * <li>namespace: the node namespace</li>
 * <li>name: the local name in the namespace</li>
 * <li>node: the full serialized XML node including the name</li>
 * </ul>
 * The table name used can be configured using the <code>tableName</code> property of the store.
 * <p>
 * Example table schema:
 * <code>CREATE TABLE properties (
 *    path         VARCHAR(1024) NOT NULL,
 *    namespace    VARCHAR(64) NOT NULL,
 *    name         VARCHAR(64) NOT NULL,
 *    node         VARCHAR(2048) NOT NULL,
 *    PRIMARY KEY (path, namespace, name)
 * )</code>
 */
public class DataSourcePropertyStore implements WebdavServlet.PropertyStore {

    protected static final StringManager sm = StringManager.getManager(DataSourcePropertyStore.class);
    private final Log log = LogFactory.getLog(DataSourcePropertyStore.class);

    /**
     * DataSource JNDI name, will be prefixed with java:comp/env for the lookup.
     */
    private String dataSourceName = "WebdavPropertyStore";

    /**
     * Table name.
     */
    private String tableName = "properties";

    private String addPropertyStatement;
    private String setPropertyStatement;
    private String removeAllPropertiesStatement;
    private String removePropertyStatement;
    private String getPropertyStatement;
    private String getPropertiesNameStatement;
    private String getPropertiesStatement;
    private String getPropertiesNodeStatement;

    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();
    private final Lock dbReadLock = dbLock.readLock();
    private final Lock dbWriteLock = dbLock.writeLock();

    /**
     * @return the DataSource JNDI name, will be prefixed with java:comp/env for the lookup.
     */
    public String getDataSourceName() {
        return this.dataSourceName;
    }

    /**
     * @param dataSourceName the DataSource JNDI name, will be prefixed with
     *  java:comp/env for the lookup.
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return the table name that will be used in the database
     */
    public String getTableName() {
        return this.tableName;
    }

    /**
     * @param tableName the table name to use in the database
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * DataSource instance being used.
     */
    protected DataSource dataSource = null;

    @Override
    public void init() {
        if (dataSource == null) {
            try {
                dataSource = (DataSource) ((new InitialContext()).lookup("java:comp/env/" + dataSourceName));
            } catch (NamingException e) {
                throw new IllegalArgumentException(sm.getString("webdavservlet.dataSourceStore.noDataSource", dataSourceName), e);
            }
        }
        addPropertyStatement = "INSERT INTO " + tableName + " (path, namespace, name, node) VALUES (?, ?, ?, ?)";
        setPropertyStatement = "UPDATE " + tableName + " SET node = ? WHERE path = ? AND namespace = ? AND name = ?";
        removeAllPropertiesStatement = "DELETE FROM " + tableName + " WHERE path = ?";
        removePropertyStatement = "DELETE FROM " + tableName + " WHERE path = ? AND namespace = ? AND name = ?";
        getPropertyStatement = "SELECT node FROM " + tableName + " WHERE path = ? AND namespace = ? AND name = ?";
        getPropertiesNameStatement = "SELECT namespace, name FROM " + tableName + " WHERE path = ?";
        getPropertiesStatement = "SELECT namespace, name, node FROM " + tableName + " WHERE path = ?";
        getPropertiesNodeStatement = "SELECT node FROM " + tableName + " WHERE path = ?";
    }

    @Override
    public void destroy() {
    }

    @Override
    public void periodicEvent() {
    }

    @Override
    public void copy(String source, String destination) {
        if (dataSource == null) {
            return;
        }
        dbWriteLock.lock();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(getPropertiesStatement)) {
            statement.setString(1, source);
            if (statement.execute()) {
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    String namespace = rs.getString(1);
                    String name = rs.getString(2);
                    String node = rs.getString(3);
                    boolean found = false;
                    try (PreparedStatement statement2 = connection.prepareStatement(getPropertyStatement)) {
                        statement2.setString(1, destination);
                        statement2.setString(2, namespace);
                        statement2.setString(3, name);
                        if (statement2.execute()) {
                            ResultSet rs2 = statement2.getResultSet();
                            if (rs2.next()) {
                                found = true;
                            }
                        }
                    }
                    if (found) {
                        try (PreparedStatement statement2 = connection.prepareStatement(setPropertyStatement)) {
                            statement2.setString(1, node);
                            statement2.setString(2, destination);
                            statement2.setString(3, namespace);
                            statement2.setString(4, name);
                            statement2.execute();
                        }
                    } else {
                        try (PreparedStatement statement2 = connection.prepareStatement(addPropertyStatement)) {
                            statement2.setString(1, destination);
                            statement2.setString(2, namespace);
                            statement2.setString(3, name);
                            statement2.setString(4, node);
                            statement2.execute();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.warn(sm.getString("webdavservlet.dataSourceStore.error", "copy", source), e);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public void delete(String resource) {
        if (dataSource == null) {
            return;
        }
        dbWriteLock.lock();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(removeAllPropertiesStatement)) {
            statement.setString(1, resource);
            statement.execute();
        } catch (SQLException e) {
            log.warn(sm.getString("webdavservlet.dataSourceStore.error", "delete", resource), e);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public boolean propfind(String resource, Node property, boolean nameOnly, XMLWriter generatedXML) {
        if (dataSource == null) {
            return false;
        }
        if (nameOnly) {
            // Add the names of all properties
            dbReadLock.lock();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(getPropertiesNameStatement)) {
                statement.setString(1, resource);
                if (statement.execute()) {
                    ResultSet rs = statement.getResultSet();
                    while (rs.next()) {
                        String namespace = rs.getString(1);
                        String name = rs.getString(2);
                        generatedXML.writeElement(null, namespace, name, XMLWriter.NO_CONTENT);
                    }
                }
            } catch (SQLException e) {
                log.warn(sm.getString("webdavservlet.dataSourceStore.error", "propfind", resource), e);
            } finally {
                dbReadLock.unlock();
            }
        } else if (property != null) {
            // Add a single property
            dbReadLock.lock();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(getPropertyStatement)) {
                statement.setString(1, resource);
                statement.setString(2, property.getNamespaceURI());
                statement.setString(3, property.getLocalName());
                if (statement.execute()) {
                    ResultSet rs = statement.getResultSet();
                    if (rs.next()) {
                        String node = rs.getString(1);
                        generatedXML.writeRaw(node);
                        return true;
                    }
                }
            } catch (SQLException e) {
                log.warn(sm.getString("webdavservlet.dataSourceStore.error", "propfind", resource), e);
            } finally {
                dbReadLock.unlock();
            }
        } else {
            // Add all properties
            dbReadLock.lock();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(getPropertiesNodeStatement)) {
                statement.setString(1, resource);
                if (statement.execute()) {
                    ResultSet rs = statement.getResultSet();
                    while (rs.next()) {
                        String node = rs.getString(1);
                        generatedXML.writeRaw(node);
                    }
                }
            } catch (SQLException e) {
                log.warn(sm.getString("webdavservlet.dataSourceStore.error", "propfind", resource), e);
            } finally {
                dbReadLock.unlock();
            }
        }
        return false;
    }

    @Override
    public void proppatch(String resource, ArrayList<ProppatchOperation> operations) {
        boolean protectedProperty = false;
        // Check for the protected properties
        for (ProppatchOperation operation : operations) {
            if (operation.getProtectedProperty()) {
                protectedProperty = true;
                operation.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
            }
        }
        if (protectedProperty) {
            for (ProppatchOperation operation : operations) {
                if (!operation.getProtectedProperty()) {
                    operation.setStatusCode(WebdavStatus.SC_FAILED_DEPENDENCY);
                }
            }
        } else {
            if (dataSource == null) {
                for (ProppatchOperation operation : operations) {
                    operation.setStatusCode(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
                return;
            }
            boolean failure = false;
            dbWriteLock.lock();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                for (ProppatchOperation operation : operations) {
                    if (operation.getUpdateType() == PropertyUpdateType.SET) {
                        Node node = operation.getPropertyNode().cloneNode(true);
                        StringWriter strWriter = new StringWriter();
                        DOMWriter domWriter = new DOMWriter(strWriter);
                        domWriter.print(node);
                        String serializedNode = strWriter.toString();
                        boolean found = false;
                        try {
                            try (PreparedStatement statement = connection.prepareStatement(getPropertyStatement)) {
                                statement.setString(1, resource);
                                statement.setString(2, node.getNamespaceURI());
                                statement.setString(3, node.getLocalName());
                                if (statement.execute()) {
                                    ResultSet rs = statement.getResultSet();
                                    if (rs.next()) {
                                        found = true;
                                    }
                                }
                            }
                            if (found) {
                                try (PreparedStatement statement = connection.prepareStatement(setPropertyStatement)) {
                                    statement.setString(1, serializedNode);
                                    statement.setString(2, resource);
                                    statement.setString(3, node.getNamespaceURI());
                                    statement.setString(4, node.getLocalName());
                                    statement.execute();
                                }
                            } else {
                                try (PreparedStatement statement = connection.prepareStatement(addPropertyStatement)) {
                                    statement.setString(1, resource);
                                    statement.setString(2, node.getNamespaceURI());
                                    statement.setString(3, node.getLocalName());
                                    statement.setString(4, serializedNode);
                                    statement.execute();
                                }
                            }
                        } catch (SQLException e) {
                            failure = true;
                            operation.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            break;
                        }
                    }
                    if (operation.getUpdateType() == PropertyUpdateType.REMOVE) {
                        Node node = operation.getPropertyNode();
                        try (PreparedStatement statement = connection.prepareStatement(removePropertyStatement)) {
                            statement.setString(1, resource);
                            statement.setString(2, node.getNamespaceURI());
                            statement.setString(3, node.getLocalName());
                            statement.execute();
                        } catch (SQLException e) {
                            failure = true;
                            operation.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            break;
                        }
                    }
                }
                if (failure) {
                    connection.rollback();
                    for (ProppatchOperation operation : operations) {
                        if (operation.getStatusCode() == HttpServletResponse.SC_OK) {
                            operation.setStatusCode(WebdavStatus.SC_FAILED_DEPENDENCY);
                        }
                    }
                } else {
                    connection.commit();
                }
            } catch (SQLException e) {
                log.warn(sm.getString("webdavservlet.dataSourceStore.error", "proppatch", resource), e);
                for (ProppatchOperation operation : operations) {
                    operation.setStatusCode(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                }
            } finally {
                dbWriteLock.unlock();
            }
        }
    }

}
