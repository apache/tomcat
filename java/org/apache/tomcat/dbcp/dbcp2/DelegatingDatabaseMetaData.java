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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

/**
 * <p>
 * A base delegating implementation of {@link DatabaseMetaData}.
 * </p>
 * <p>
 * Methods that create {@link ResultSet} objects are wrapped to create {@link DelegatingResultSet} objects and the
 * remaining methods simply call the corresponding method on the "delegate" provided in the constructor.
 * </p>
 *
 * @since 2.0
 */
public class DelegatingDatabaseMetaData implements DatabaseMetaData {

    /** My delegate {@link DatabaseMetaData} */
    private final DatabaseMetaData databaseMetaData;

    /** The connection that created me. **/
    private final DelegatingConnection<?> connection;

    /**
     * Constructs a new instance for the given delegating connection and database meta data.
     *
     * @param connection
     *            the delegating connection
     * @param databaseMetaData
     *            the database meta data
     */
    public DelegatingDatabaseMetaData(final DelegatingConnection<?> connection,
            final DatabaseMetaData databaseMetaData) {
        super();
        this.connection = connection;
        this.databaseMetaData = databaseMetaData;
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        try {
            return databaseMetaData.allProceduresAreCallable();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        try {
            return databaseMetaData.allTablesAreSelectable();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        try {
            return databaseMetaData.autoCommitFailureClosesAllResultSets();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        try {
            return databaseMetaData.dataDefinitionCausesTransactionCommit();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        try {
            return databaseMetaData.dataDefinitionIgnoredInTransactions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean deletesAreDetected(final int type) throws SQLException {
        try {
            return databaseMetaData.deletesAreDetected(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        try {
            return databaseMetaData.doesMaxRowSizeIncludeBlobs();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        connection.checkOpen();
        try {
            return Jdbc41Bridge.generatedKeyAlwaysReturned(databaseMetaData);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern, final String typeNamePattern,
            final String attributeNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getAttributes(catalog, schemaPattern, typeNamePattern, attributeNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema, final String table,
            final int scope, final boolean nullable) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getBestRowIdentifier(catalog, schema, table, scope, nullable));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getCatalogs());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        try {
            return databaseMetaData.getCatalogSeparator();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        try {
            return databaseMetaData.getCatalogTerm();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getClientInfoProperties());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema, final String table,
            final String columnNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getColumnPrivileges(catalog, schema, table, columnNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern, final String tableNamePattern,
            final String columnNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public ResultSet getCrossReference(final String parentCatalog, final String parentSchema, final String parentTable,
            final String foreignCatalog, final String foreignSchema, final String foreignTable) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getCrossReference(parentCatalog,
                    parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        try {
            return databaseMetaData.getDatabaseMajorVersion();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        try {
            return databaseMetaData.getDatabaseMinorVersion();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        try {
            return databaseMetaData.getDatabaseProductName();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        try {
            return databaseMetaData.getDatabaseProductVersion();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        try {
            return databaseMetaData.getDefaultTransactionIsolation();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /**
     * Gets the underlying database meta data.
     *
     * @return The underlying database meta data.
     */
    public DatabaseMetaData getDelegate() {
        return databaseMetaData;
    }

    @Override
    public int getDriverMajorVersion() {
        return databaseMetaData.getDriverMajorVersion();
    }

    @Override
    public int getDriverMinorVersion() {
        return databaseMetaData.getDriverMinorVersion();
    }

    @Override
    public String getDriverName() throws SQLException {
        try {
            return databaseMetaData.getDriverName();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getDriverVersion() throws SQLException {
        try {
            return databaseMetaData.getDriverVersion();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getExportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getExportedKeys(catalog, schema, table));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        try {
            return databaseMetaData.getExtraNameCharacters();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctionColumns(final String catalog, final String schemaPattern,
            final String functionNamePattern, final String columnNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getFunctionColumns(catalog,
                    schemaPattern, functionNamePattern, columnNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctions(final String catalog, final String schemaPattern, final String functionNamePattern)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getFunctions(catalog, schemaPattern, functionNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        try {
            return databaseMetaData.getIdentifierQuoteString();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getImportedKeys(catalog, schema, table));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table, final boolean unique,
            final boolean approximate) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getIndexInfo(catalog, schema, table, unique, approximate));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    /**
     * If my underlying {@link ResultSet} is not a {@code DelegatingResultSet}, returns it, otherwise recursively
     * invokes this method on my delegate.
     * <p>
     * Hence this method will return the first delegate that is not a {@code DelegatingResultSet}, or {@code null} when
     * no non-{@code DelegatingResultSet} delegate can be found by traversing this chain.
     * </p>
     * <p>
     * This method is useful when you may have nested {@code DelegatingResultSet}s, and you want to make sure to obtain
     * a "genuine" {@link ResultSet}.
     * </p>
     *
     * @return the innermost database meta data.
     */
    public DatabaseMetaData getInnermostDelegate() {
        DatabaseMetaData m = databaseMetaData;
        while (m != null && m instanceof DelegatingDatabaseMetaData) {
            m = ((DelegatingDatabaseMetaData) m).getDelegate();
            if (this == m) {
                return null;
            }
        }
        return m;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        try {
            return databaseMetaData.getJDBCMajorVersion();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        try {
            return databaseMetaData.getJDBCMinorVersion();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        try {
            return databaseMetaData.getMaxBinaryLiteralLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxCatalogNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        try {
            return databaseMetaData.getMaxCharLiteralLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnsInGroupBy();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnsInIndex();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnsInOrderBy();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnsInSelect();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        try {
            return databaseMetaData.getMaxColumnsInTable();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxConnections() throws SQLException {
        try {
            return databaseMetaData.getMaxConnections();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxCursorNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        try {
            return databaseMetaData.getMaxIndexLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /**
     * @since 2.5.0
     */
    @Override
    public long getMaxLogicalLobSize() throws SQLException {
        try {
            return databaseMetaData.getMaxLogicalLobSize();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxProcedureNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        try {
            return databaseMetaData.getMaxRowSize();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxSchemaNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        try {
            return databaseMetaData.getMaxStatementLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxStatements() throws SQLException {
        try {
            return databaseMetaData.getMaxStatements();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxTableNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        try {
            return databaseMetaData.getMaxTablesInSelect();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        try {
            return databaseMetaData.getMaxUserNameLength();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        try {
            return databaseMetaData.getNumericFunctions();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema, final String table) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getPrimaryKeys(catalog, schema, table));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getProcedureColumns(final String catalog, final String schemaPattern,
            final String procedureNamePattern, final String columnNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getProcedureColumns(catalog,
                    schemaPattern, procedureNamePattern, columnNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern, final String procedureNamePattern)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getProcedures(catalog, schemaPattern, procedureNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        try {
            return databaseMetaData.getProcedureTerm();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getPseudoColumns(final String catalog, final String schemaPattern, final String tableNamePattern,
            final String columnNamePattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, Jdbc41Bridge.getPseudoColumns(databaseMetaData,
                    catalog, schemaPattern, tableNamePattern, columnNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        try {
            return databaseMetaData.getResultSetHoldability();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        try {
            return databaseMetaData.getRowIdLifetime();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getSchemas());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getSchemas(catalog, schemaPattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        try {
            return databaseMetaData.getSchemaTerm();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        try {
            return databaseMetaData.getSearchStringEscape();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        try {
            return databaseMetaData.getSQLKeywords();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getSQLStateType() throws SQLException {
        try {
            return databaseMetaData.getSQLStateType();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public String getStringFunctions() throws SQLException {
        try {
            return databaseMetaData.getStringFunctions();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSuperTables(final String catalog, final String schemaPattern, final String tableNamePattern)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getSuperTables(catalog, schemaPattern, tableNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSuperTypes(final String catalog, final String schemaPattern, final String typeNamePattern)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getSuperTypes(catalog, schemaPattern, typeNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        try {
            return databaseMetaData.getSystemFunctions();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTablePrivileges(final String catalog, final String schemaPattern, final String tableNamePattern)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getTablePrivileges(catalog, schemaPattern, tableNamePattern));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern, final String tableNamePattern,
            final String[] types) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getTables(catalog, schemaPattern, tableNamePattern, types));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getTableTypes());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        try {
            return databaseMetaData.getTimeDateFunctions();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection, databaseMetaData.getTypeInfo());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getUDTs(final String catalog, final String schemaPattern, final String typeNamePattern,
            final int[] types) throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getUDTs(catalog, schemaPattern, typeNamePattern, types));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getURL() throws SQLException {
        try {
            return databaseMetaData.getURL();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getUserName() throws SQLException {
        try {
            return databaseMetaData.getUserName();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getVersionColumns(final String catalog, final String schema, final String table)
            throws SQLException {
        connection.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(connection,
                    databaseMetaData.getVersionColumns(catalog, schema, table));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (connection != null) {
            connection.handleException(e);
        } else {
            throw e;
        }
    }

    @Override
    public boolean insertsAreDetected(final int type) throws SQLException {
        try {
            return databaseMetaData.insertsAreDetected(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        try {
            return databaseMetaData.isCatalogAtStart();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        try {
            return databaseMetaData.isReadOnly();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(databaseMetaData.getClass())) {
            return true;
        } else {
            return databaseMetaData.isWrapperFor(iface);
        }
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        try {
            return databaseMetaData.locatorsUpdateCopy();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        try {
            return databaseMetaData.nullPlusNonNullIsNull();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        try {
            return databaseMetaData.nullsAreSortedAtEnd();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        try {
            return databaseMetaData.nullsAreSortedAtStart();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        try {
            return databaseMetaData.nullsAreSortedHigh();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        try {
            return databaseMetaData.nullsAreSortedLow();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean othersDeletesAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.othersDeletesAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean othersInsertsAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.othersInsertsAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean othersUpdatesAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.othersUpdatesAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean ownDeletesAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.ownDeletesAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean ownInsertsAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.ownInsertsAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean ownUpdatesAreVisible(final int type) throws SQLException {
        try {
            return databaseMetaData.ownUpdatesAreVisible(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesLowerCaseIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesLowerCaseQuotedIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesMixedCaseIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesMixedCaseQuotedIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesUpperCaseIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        try {
            return databaseMetaData.storesUpperCaseQuotedIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        try {
            return databaseMetaData.supportsAlterTableWithAddColumn();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        try {
            return databaseMetaData.supportsAlterTableWithDropColumn();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        try {
            return databaseMetaData.supportsANSI92EntryLevelSQL();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        try {
            return databaseMetaData.supportsANSI92FullSQL();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        try {
            return databaseMetaData.supportsANSI92IntermediateSQL();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        try {
            return databaseMetaData.supportsBatchUpdates();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        try {
            return databaseMetaData.supportsCatalogsInDataManipulation();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsCatalogsInIndexDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsCatalogsInPrivilegeDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        try {
            return databaseMetaData.supportsCatalogsInProcedureCalls();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsCatalogsInTableDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        try {
            return databaseMetaData.supportsColumnAliasing();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        try {
            return databaseMetaData.supportsConvert();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsConvert(final int fromType, final int toType) throws SQLException {
        try {
            return databaseMetaData.supportsConvert(fromType, toType);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        try {
            return databaseMetaData.supportsCoreSQLGrammar();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        try {
            return databaseMetaData.supportsCorrelatedSubqueries();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        try {
            return databaseMetaData.supportsDataDefinitionAndDataManipulationTransactions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        try {
            return databaseMetaData.supportsDataManipulationTransactionsOnly();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        try {
            return databaseMetaData.supportsDifferentTableCorrelationNames();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        try {
            return databaseMetaData.supportsExpressionsInOrderBy();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        try {
            return databaseMetaData.supportsExtendedSQLGrammar();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        try {
            return databaseMetaData.supportsFullOuterJoins();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        try {
            return databaseMetaData.supportsGetGeneratedKeys();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        try {
            return databaseMetaData.supportsGroupBy();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        try {
            return databaseMetaData.supportsGroupByBeyondSelect();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        try {
            return databaseMetaData.supportsGroupByUnrelated();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        try {
            return databaseMetaData.supportsIntegrityEnhancementFacility();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        try {
            return databaseMetaData.supportsLikeEscapeClause();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        try {
            return databaseMetaData.supportsLimitedOuterJoins();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        try {
            return databaseMetaData.supportsMinimumSQLGrammar();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        try {
            return databaseMetaData.supportsMixedCaseIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        try {
            return databaseMetaData.supportsMixedCaseQuotedIdentifiers();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        try {
            return databaseMetaData.supportsMultipleOpenResults();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        try {
            return databaseMetaData.supportsMultipleResultSets();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        try {
            return databaseMetaData.supportsMultipleTransactions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        try {
            return databaseMetaData.supportsNamedParameters();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        try {
            return databaseMetaData.supportsNonNullableColumns();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        try {
            return databaseMetaData.supportsOpenCursorsAcrossCommit();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        try {
            return databaseMetaData.supportsOpenCursorsAcrossRollback();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        try {
            return databaseMetaData.supportsOpenStatementsAcrossCommit();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        try {
            return databaseMetaData.supportsOpenStatementsAcrossRollback();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        try {
            return databaseMetaData.supportsOrderByUnrelated();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        try {
            return databaseMetaData.supportsOuterJoins();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        try {
            return databaseMetaData.supportsPositionedDelete();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        try {
            return databaseMetaData.supportsPositionedUpdate();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /**
     * @since 2.5.0
     */
    @Override
    public boolean supportsRefCursors() throws SQLException {
        try {
            return databaseMetaData.supportsRefCursors();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency) throws SQLException {
        try {
            return databaseMetaData.supportsResultSetConcurrency(type, concurrency);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability) throws SQLException {
        try {
            return databaseMetaData.supportsResultSetHoldability(holdability);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        try {
            return databaseMetaData.supportsResultSetType(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        try {
            return databaseMetaData.supportsSavepoints();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        try {
            return databaseMetaData.supportsSchemasInDataManipulation();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsSchemasInIndexDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsSchemasInPrivilegeDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        try {
            return databaseMetaData.supportsSchemasInProcedureCalls();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        try {
            return databaseMetaData.supportsSchemasInTableDefinitions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        try {
            return databaseMetaData.supportsSelectForUpdate();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        try {
            return databaseMetaData.supportsStatementPooling();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        try {
            return databaseMetaData.supportsStoredFunctionsUsingCallSyntax();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        try {
            return databaseMetaData.supportsStoredProcedures();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /* JDBC_4_ANT_KEY_BEGIN */

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        try {
            return databaseMetaData.supportsSubqueriesInComparisons();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        try {
            return databaseMetaData.supportsSubqueriesInExists();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        try {
            return databaseMetaData.supportsSubqueriesInIns();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        try {
            return databaseMetaData.supportsSubqueriesInQuantifieds();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        try {
            return databaseMetaData.supportsTableCorrelationNames();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsTransactionIsolationLevel(final int level) throws SQLException {
        try {
            return databaseMetaData.supportsTransactionIsolationLevel(level);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        try {
            return databaseMetaData.supportsTransactions();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        try {
            return databaseMetaData.supportsUnion();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        try {
            return databaseMetaData.supportsUnionAll();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /* JDBC_4_ANT_KEY_END */

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(databaseMetaData.getClass())) {
            return iface.cast(databaseMetaData);
        } else {
            return databaseMetaData.unwrap(iface);
        }
    }

    @Override
    public boolean updatesAreDetected(final int type) throws SQLException {
        try {
            return databaseMetaData.updatesAreDetected(type);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        try {
            return databaseMetaData.usesLocalFilePerTable();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        try {
            return databaseMetaData.usesLocalFiles();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }
}
