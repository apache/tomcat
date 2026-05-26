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

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;

/**
 * Implementation of the {@link org.apache.catalina.Store Store} interface that stores serialized session objects in a
 * database. Sessions that are saved are still subject to being expired based on inactivity.
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
     * Return an array containing the session identifiers of all Sessions currently saved in this Store. If there are no
     * such Sessions, a zero-length array is returned.
     *
     * @param expiredOnly flag, whether only keys of expired sessions should be returned
     *
     * @return array containing the list of session IDs
     */
    private String[] keys(boolean expiredOnly) throws IOException {
        String sqlTmp = "SELECT " + sessionIdCol + " FROM " + sessionTable + " WHERE " + sessionAppCol + " = ?";
        if (expiredOnly) {
            sqlTmp += " AND (" + sessionLastAccessedCol + " + " + sessionMaxInactiveCol + " * 1000 < ?)";
        }
        final String keysSql = sqlTmp;

        String[] keys = withRetry((ConnectionOperation<String[],IOException>) conn -> {
            try (PreparedStatement preparedKeysSql = conn.prepareStatement(keysSql)) {
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
                    return tmpkeys.toArray(new String[0]);
                }
            }
        });

        return keys == null ? new String[0] : keys;
    }

    @Override
    public int getSize() throws IOException {
        String sizeSql = "SELECT COUNT(" + sessionIdCol + ") FROM " + sessionTable + " WHERE " + sessionAppCol + " = ?";

        Integer size = withRetry((ConnectionOperation<Integer,IOException>) conn -> {
            try (PreparedStatement preparedSizeSql = conn.prepareStatement(sizeSql)) {
                preparedSizeSql.setString(1, getName());
                try (ResultSet rst = preparedSizeSql.executeQuery()) {
                    if (rst.next()) {
                        return Integer.valueOf(rst.getInt(1));
                    } else {
                        return Integer.valueOf(0);
                    }
                }
            }
        });

        return size == null ? 0 : size.intValue();
    }

    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        org.apache.catalina.Context context = getManager().getContext();
        Log contextLog = context.getLogger();
        String loadSql = "SELECT " + sessionIdCol + ", " + sessionDataCol + " FROM " + sessionTable + " WHERE " +
                sessionIdCol + " = ? AND " + sessionAppCol + " = ?";

        Session session = withRetry((ConnectionOperation<StandardSession,ClassNotFoundException>) conn -> {
            ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);

            try (PreparedStatement preparedLoadSql = conn.prepareStatement(loadSql)) {
                preparedLoadSql.setString(1, id);
                preparedLoadSql.setString(2, getName());
                try (ResultSet rst = preparedLoadSql.executeQuery()) {
                    if (rst.next()) {
                        try (ObjectInputStream ois = getObjectInputStream(rst.getBinaryStream(2))) {
                            if (contextLog.isTraceEnabled()) {
                                contextLog.trace(sm.getString(getStoreName() + ".loading", id, sessionTable));
                            }

                            StandardSession _session = (StandardSession) manager.createEmptySession();
                            _session.readObjectData(ois);
                            _session.setManager(manager);
                            return _session;
                        }
                    } else if (context.getLogger().isDebugEnabled()) {
                        contextLog.debug(getStoreName() + ": No persisted data object found");
                    }
                    return null;
                }
            } finally {
                context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
            }
        });
        return session;
    }

    @Override
    public void remove(String id) throws IOException {
        withRetry(conn -> {
            remove(id, conn);
            return null;
        });

        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger().trace(sm.getString(getStoreName() + ".removing", id, sessionTable));
        }
    }

    /**
     * Remove the Session with the specified session identifier from this Store, if present. If no such Session is
     * present, this method takes no action.
     *
     * @param id    Session identifier of the Session to be removed
     * @param _conn open connection to be used
     *
     * @throws SQLException if an error occurs while talking to the database
     */
    private void remove(String id, Connection _conn) throws SQLException {
        String removeSql =
                "DELETE FROM " + sessionTable + " WHERE " + sessionIdCol + " = ?  AND " + sessionAppCol + " = ?";
        try (PreparedStatement preparedRemoveSql = _conn.prepareStatement(removeSql)) {
            preparedRemoveSql.setString(1, id);
            preparedRemoveSql.setString(2, getName());
            preparedRemoveSql.execute();
        }
    }

    @Override
    public void clear() throws IOException {
        String clearSql = "DELETE FROM " + sessionTable + " WHERE " + sessionAppCol + " = ?";

        withRetry(conn -> {
            try (PreparedStatement preparedClearSql = conn.prepareStatement(clearSql)) {
                preparedClearSql.setString(1, getName());
                preparedClearSql.execute();
            }
            return null;
        });
    }

    @Override
    public void save(Session session) throws IOException {
        String saveSql = "INSERT INTO " + sessionTable + " (" + sessionIdCol + ", " + sessionAppCol + ", " +
                sessionDataCol + ", " + sessionValidCol + ", " + sessionMaxInactiveCol + ", " + sessionLastAccessedCol +
                ") VALUES (?, ?, ?, ?, ?, ?)";

        synchronized (session) {

            // First serialize session
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
                ((StandardSession) session).writeObjectData(oos);
            }
            byte[] obs = bos.toByteArray();

            withRetry(conn -> {
                // Remove session if it exists and insert again.
                remove(session.getIdInternal(), conn);

                int size = obs.length;
                try (ByteArrayInputStream bis = new ByteArrayInputStream(obs, 0, size);
                        InputStream in = new BufferedInputStream(bis, size);
                        PreparedStatement preparedSaveSql = conn.prepareStatement(saveSql)) {
                    preparedSaveSql.setString(1, session.getIdInternal());
                    preparedSaveSql.setString(2, getName());
                    preparedSaveSql.setBinaryStream(3, in, size);
                    preparedSaveSql.setString(4, session.isValid() ? "1" : "0");
                    preparedSaveSql.setInt(5, session.getMaxInactiveInterval());
                    preparedSaveSql.setLong(6, session.getLastAccessedTime());
                    preparedSaveSql.execute();
                }
                return null;
            });
        }

        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger()
                    .trace(sm.getString(getStoreName() + ".saving", session.getIdInternal(), sessionTable));
        }
    }


    // --------------------------------------------------------- Protected Methods

    /**
     * Open (if necessary) and return a database connection for use by this Store.
     *
     * @return database connection ready to use
     *
     * @exception SQLException if a database error occurs
     */
    @Override
    protected Connection open() throws SQLException {
        if (dataSourceName != null && dataSource == null) {
            org.apache.catalina.Context context = getManager().getContext();
            if (getLocalDataSource()) {
                ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);
                try {
                    Context envCtx = (Context) (new InitialContext()).lookup("java:comp/env");
                    this.dataSource = (DataSource) envCtx.lookup(this.dataSourceName);
                } catch (NamingException e) {
                    context.getLogger().error(sm.getString("dataSourceStore.wrongDataSource", this.dataSourceName), e);
                } finally {
                    context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
                }
            } else {
                try {
                    // This should be the normal way to lookup for the global in the global context (no comp/env)
                    Service service = Container.getService(context);
                    if (service != null) {
                        Server server = service.getServer();
                        if (server != null) {
                            Context namingContext = server.getGlobalNamingContext();
                            this.dataSource = (DataSource) namingContext.lookup(dataSourceName);
                        }
                    }
                } catch (NamingException e) {
                    // Ignore, try another way for compatibility
                }
                if (this.dataSource == null) {
                    try {
                        Context envCtx = (Context) (new InitialContext()).lookup("java:comp/env");
                        this.dataSource = (DataSource) envCtx.lookup(this.dataSourceName);
                    } catch (NamingException e) {
                        context.getLogger().error(sm.getString("dataSourceStore.wrongDataSource", this.dataSourceName),
                                e);
                    }
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
            manager.getContext().getLogger().error(sm.getString(getStoreName() + ".close"), e); // Just log it here
        }
    }


    private <T, E extends Exception> T withRetry(ConnectionOperation<T,E> operation) throws IOException, E {
        SQLException sqlException = null;

        int numberOfTries = 2;
        while (numberOfTries > 0) {
            /*
             * TODO: To further improve consistency, consider refactoring getConnection so an IOException is thrown here
             * with a nested SQLException if a connection cannot be obtained. This would also allow some of the null
             * handling on return to be removed.
             */
            Connection _conn = getConnection();
            if (_conn == null) {
                return null;
            }

            try {
                return operation.execute(_conn);
            } catch (SQLException e) {
                // Retain the first exception to use as the cause if all retries fail
                if (sqlException == null) {
                    sqlException = e;
                }
            } finally {
                release(_conn);
            }
            numberOfTries--;
        }

        throw new IOException(sm.getString(getStoreName() + ".SQLException"), sqlException);
    }


    /**
     * Functional interface for store operation. Used with {@link DataSourceStore#withRetry(ConnectionOperation)} to
     * reduce code duplication.
     *
     * @param <T> The return type for the operation
     * @param <E> The additional exception type thrown by this operation. If no additional exception type is thrown then
     *                specify IOException
     */
    @FunctionalInterface
    private interface ConnectionOperation<T, E extends Exception> {
        T execute(Connection connection) throws IOException, SQLException, E;
    }
}
