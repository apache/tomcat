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
package org.apache.catalina.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.catalina.Globals;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;

/**
 * Implementation of the {@link org.apache.catalina.Store Store}
 * interface that stores serialized session objects in a database.
 * Sessions that are saved are still subject to being expired
 * based on inactivity.
 *
 * @author Bip Thelin
 */
@SuppressWarnings("deprecation")
public class DataSourceStore extends JDBCStore {

    // --------------------------------------------------------- Public Methods

    @Override
    public String[] expiredKeys() throws IOException {
        return keys(true);
    }

    @Override
    public String[] keys() throws IOException {
        return keys(false);
    }

    /**
     * Return an array containing the session identifiers of all Sessions
     * currently saved in this Store.  If there are no such Sessions, a
     * zero-length array is returned.
     *
     * @param expiredOnly flag, whether only keys of expired sessions should
     *        be returned
     * @return array containing the list of session IDs
     *
     * @exception IOException if an input/output error occurred
     */
    private String[] keys(boolean expiredOnly) throws IOException {
        String keys[] = null;
        int numberOfTries = 2;
        while (numberOfTries > 0) {

            Connection _conn = getConnection();
            if (_conn == null) {
                return new String[0];
            }
            try {

                String keysSql = "SELECT " + sessionIdCol + " FROM "
                        + sessionTable + " WHERE " + sessionAppCol + " = ?";
                if (expiredOnly) {
                    keysSql += " AND (" + sessionLastAccessedCol + " + "
                            + sessionMaxInactiveCol + " * 1000 < ?)";
                }
                try (PreparedStatement preparedKeysSql = _conn.prepareStatement(keysSql)) {
                    preparedKeysSql.setString(1, getName());
                    if (expiredOnly) {
                        preparedKeysSql.setLong(2, System.currentTimeMillis());
                    }
                    try (ResultSet rst = preparedKeysSql.executeQuery()) {
                        List<String> tmpkeys = new ArrayList<>();
                        if (rst != null) {
                            while (rst.next()) {
                                tmpkeys.add(rst.getString(1));
                            }
                        }
                        keys = tmpkeys.toArray(new String[0]);
                        // Break out after the finally block
                        numberOfTries = 0;
                    }
                }
            } catch (SQLException e) {
                manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                keys = new String[0];
                // Close the connection so that it gets reopened next time
            } finally {
                release(_conn);
            }
            numberOfTries--;
        }
        return keys;
    }

    /**
     * Return an integer containing a count of all Sessions
     * currently saved in this Store.  If there are no Sessions,
     * <code>0</code> is returned.
     *
     * @return the count of all sessions currently saved in this Store
     *
     * @exception IOException if an input/output error occurred
     */
    @Override
    public int getSize() throws IOException {
        int size = 0;
        String sizeSql = "SELECT COUNT(" + sessionIdCol
                + ") FROM " + sessionTable + " WHERE "
                + sessionAppCol + " = ?";

        int numberOfTries = 2;
        while (numberOfTries > 0) {
            Connection _conn = getConnection();

            if (_conn == null) {
                return size;
            }

            try (PreparedStatement preparedSizeSql = _conn.prepareStatement(sizeSql)){
                preparedSizeSql.setString(1, getName());
                try (ResultSet rst = preparedSizeSql.executeQuery()) {
                    if (rst.next()) {
                        size = rst.getInt(1);
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                }
            } catch (SQLException e) {
                manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
            } finally {
                release(_conn);
            }
            numberOfTries--;
        }
        return size;
    }

    /**
     * Load the Session associated with the id <code>id</code>.
     * If no such session is found <code>null</code> is returned.
     *
     * @param id a value of type <code>String</code>
     * @return the stored <code>Session</code>
     * @exception ClassNotFoundException if an error occurs
     * @exception IOException if an input/output error occurred
     */
    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        StandardSession _session = null;
        org.apache.catalina.Context context = getManager().getContext();
        Log contextLog = context.getLogger();

        int numberOfTries = 2;
        String loadSql = "SELECT " + sessionIdCol + ", "
                + sessionDataCol + " FROM " + sessionTable
                + " WHERE " + sessionIdCol + " = ? AND "
                + sessionAppCol + " = ?";
        while (numberOfTries > 0) {
            Connection _conn = getConnection();
            if (_conn == null) {
                return null;
            }

            ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);

            try (PreparedStatement preparedLoadSql = _conn.prepareStatement(loadSql)){
                preparedLoadSql.setString(1, id);
                preparedLoadSql.setString(2, getName());
                try (ResultSet rst = preparedLoadSql.executeQuery()) {
                    if (rst.next()) {
                        try (ObjectInputStream ois =
                                getObjectInputStream(rst.getBinaryStream(2))) {
                            if (contextLog.isDebugEnabled()) {
                                contextLog.debug(sm.getString(
                                        getStoreName() + ".loading", id, sessionTable));
                            }

                            _session = (StandardSession) manager.createEmptySession();
                            _session.readObjectData(ois);
                            _session.setManager(manager);
                        }
                    } else if (context.getLogger().isDebugEnabled()) {
                        contextLog.debug(getStoreName() + ": No persisted data object found");
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                }
            } catch (SQLException e) {
                contextLog.error(sm.getString(getStoreName() + ".SQLException", e));
            } finally {
                context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
                release(_conn);
            }
            numberOfTries--;
        }
        return _session;
    }

    /**
     * Remove the Session with the specified session identifier from
     * this Store, if present.  If no such Session is present, this method
     * takes no action.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void remove(String id) throws IOException {

        int numberOfTries = 2;
        while (numberOfTries > 0) {
            Connection _conn = getConnection();

            if (_conn == null) {
                return;
            }

            try {
                remove(id, _conn);
                // Break out after the finally block
                numberOfTries = 0;
            } catch (SQLException e) {
                manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
            } finally {
                release(_conn);
            }
            numberOfTries--;
        }

        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".removing", id, sessionTable));
        }
    }

    /**
     * Remove the Session with the specified session identifier from
     * this Store, if present.  If no such Session is present, this method
     * takes no action.
     *
     * @param id Session identifier of the Session to be removed
     * @param _conn open connection to be used
     * @throws SQLException if an error occurs while talking to the database
     */
    private void remove(String id, Connection _conn) throws SQLException {
        String removeSql = "DELETE FROM " + sessionTable
                + " WHERE " + sessionIdCol + " = ?  AND "
                + sessionAppCol + " = ?";
        try (PreparedStatement preparedRemoveSql = _conn.prepareStatement(removeSql)) {
            preparedRemoveSql.setString(1, id);
            preparedRemoveSql.setString(2, getName());
            preparedRemoveSql.execute();
        }
    }

    /**
     * Remove all of the Sessions in this Store.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void clear() throws IOException {
        String clearSql = "DELETE FROM " + sessionTable
                + " WHERE " + sessionAppCol + " = ?";

        int numberOfTries = 2;
        while (numberOfTries > 0) {
            Connection _conn = getConnection();
            if (_conn == null) {
                return;
            }

            try (PreparedStatement preparedClearSql = _conn.prepareStatement(clearSql)){
                preparedClearSql.setString(1, getName());
                preparedClearSql.execute();
                // Break out after the finally block
                numberOfTries = 0;
            } catch (SQLException e) {
                manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
            } finally {
                release(_conn);
            }
            numberOfTries--;
        }
    }

    /**
     * Save a session to the Store.
     *
     * @param session the session to be stored
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void save(Session session) throws IOException {
        ByteArrayOutputStream bos = null;
        String saveSql = "INSERT INTO " + sessionTable + " ("
                + sessionIdCol + ", " + sessionAppCol + ", "
                + sessionDataCol + ", " + sessionValidCol
                + ", " + sessionMaxInactiveCol + ", "
                + sessionLastAccessedCol
                + ") VALUES (?, ?, ?, ?, ?, ?)";

        synchronized (session) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                    // If sessions already exist in DB, remove and insert again.
                    // TODO:
                    // * Check if ID exists in database and if so use UPDATE.
                    remove(session.getIdInternal(), _conn);

                    bos = new ByteArrayOutputStream();
                    try (ObjectOutputStream oos =
                            new ObjectOutputStream(new BufferedOutputStream(bos))) {
                        ((StandardSession) session).writeObjectData(oos);
                    }
                    byte[] obs = bos.toByteArray();
                    int size = obs.length;
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(obs, 0, size);
                            InputStream in = new BufferedInputStream(bis, size);
                            PreparedStatement preparedSaveSql = _conn.prepareStatement(saveSql)) {
                        preparedSaveSql.setString(1, session.getIdInternal());
                        preparedSaveSql.setString(2, getName());
                        preparedSaveSql.setBinaryStream(3, in, size);
                        preparedSaveSql.setString(4, session.isValid() ? "1" : "0");
                        preparedSaveSql.setInt(5, session.getMaxInactiveInterval());
                        preparedSaveSql.setLong(6, session.getLastAccessedTime());
                        preparedSaveSql.execute();
                        // Break out after the finally block
                        numberOfTries = 0;
                    }
                } catch (SQLException e) {
                    manager.getContext().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                } catch (IOException e) {
                    // Ignore
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(sm.getString(getStoreName() + ".saving",
                    session.getIdInternal(), sessionTable));
        }
    }


    // --------------------------------------------------------- Protected Methods

    /**
     * Open (if necessary) and return a database connection for use by
     * this Store.
     *
     * @return database connection ready to use
     *
     * @exception SQLException if a database error occurs
     */
    @Override
    protected Connection open() throws SQLException {
        if (dataSourceName != null && dataSource == null) {
            org.apache.catalina.Context context = getManager().getContext();
            ClassLoader oldThreadContextCL = null;
            if (getLocalDataSource()) {
                oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);
            }

            Context initCtx;
            try {
                initCtx = new InitialContext();
                Context envCtx = (Context) initCtx.lookup("java:comp/env");
                this.dataSource = (DataSource) envCtx.lookup(this.dataSourceName);
            } catch (NamingException e) {
                context.getLogger().error(
                        sm.getString(getStoreName() + ".wrongDataSource",
                                this.dataSourceName), e);
            } finally {
                if (getLocalDataSource()) {
                    context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
                }
            }
        }

        if (dataSource != null) {
            return dataSource.getConnection();
        } else {
            throw new IllegalStateException(sm.getString(getStoreName() + ".missingDataSource"));
        }
    }

    /**
     * Close the specified database connection.
     *
     * @param dbConnection The connection to be closed
     */
    @Override
    protected void close(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null) {
            return;
        }

        // Commit if autoCommit is false
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".commitSQLException"), e);
        }

        // Close this database connection, and log any errors
        try {
            dbConnection.close();
        } catch (SQLException e) {
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".close", e.toString())); // Just log it here
        }
    }

}
