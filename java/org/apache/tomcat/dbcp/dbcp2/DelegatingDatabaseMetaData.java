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
 * <p>A base delegating implementation of {@link DatabaseMetaData}.</p>
 *
 * <p>Methods that create {@link ResultSet} objects are wrapped to
 * create {@link DelegatingResultSet} objects and the remaining methods
 * simply call the corresponding method on the "delegate"
 * provided in the constructor.</p>
 * @since 2.0
 */
public class DelegatingDatabaseMetaData implements DatabaseMetaData {

    /** My delegate {@link DatabaseMetaData} */
    private final DatabaseMetaData _meta;

    /** The connection that created me. **/
    private final DelegatingConnection<?> _conn;

    public DelegatingDatabaseMetaData(final DelegatingConnection<?> c,
            final DatabaseMetaData m) {
        super();
        _conn = c;
        _meta = m;
    }

    public DatabaseMetaData getDelegate() {
        return _meta;
    }

    /**
     * If my underlying {@link ResultSet} is not a
     * {@code DelegatingResultSet}, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a {@code DelegatingResultSet},
     * or {@code null} when no non-{@code DelegatingResultSet}
     * delegate can be found by transversing this chain.
     * <p>
     * This method is useful when you may have nested
     * {@code DelegatingResultSet}s, and you want to make
     * sure to obtain a "genuine" {@link ResultSet}.
     * @return the database meta data
     */
    public DatabaseMetaData getInnermostDelegate() {
        DatabaseMetaData m = _meta;
        while(m != null && m instanceof DelegatingDatabaseMetaData) {
            m = ((DelegatingDatabaseMetaData)m).getDelegate();
            if(this == m) {
                return null;
            }
        }
        return m;
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (_conn != null) {
            _conn.handleException(e);
        }
        else {
            throw e;
        }
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        try { return _meta.allProceduresAreCallable(); }
          catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        try { return _meta.allTablesAreSelectable(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        try { return _meta.dataDefinitionCausesTransactionCommit(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        try { return _meta.dataDefinitionIgnoredInTransactions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean deletesAreDetected(final int type) throws SQLException {
        try { return _meta.deletesAreDetected(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        try { return _meta.doesMaxRowSizeIncludeBlobs(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern,
            final String typeNamePattern, final String attributeNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,_meta.getAttributes(
                    catalog, schemaPattern, typeNamePattern,
                    attributeNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema,
            final String table, final int scope, final boolean nullable) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getBestRowIdentifier(catalog, schema, table, scope,
                            nullable));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        try { return _meta.getCatalogSeparator(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        try { return _meta.getCatalogTerm(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getCatalogs());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema,
            final String table, final String columnNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getColumnPrivileges(catalog, schema, table,
                            columnNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern,
            final String tableNamePattern, final String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getColumns(catalog, schemaPattern, tableNamePattern,
                            columnNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return _conn;
    }

    @Override
    public ResultSet getCrossReference(final String parentCatalog,
            final String parentSchema, final String parentTable, final String foreignCatalog,
            final String foreignSchema, final String foreignTable) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getCrossReference(parentCatalog, parentSchema,
                            parentTable, foreignCatalog, foreignSchema,
                            foreignTable));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        try { return _meta.getDatabaseMajorVersion(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        try { return _meta.getDatabaseMinorVersion(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        try { return _meta.getDatabaseProductName(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        try { return _meta.getDatabaseProductVersion(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        try { return _meta.getDefaultTransactionIsolation(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getDriverMajorVersion() {return _meta.getDriverMajorVersion();}

    @Override
    public int getDriverMinorVersion() {return _meta.getDriverMinorVersion();}

    @Override
    public String getDriverName() throws SQLException {
        try { return _meta.getDriverName(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getDriverVersion() throws SQLException {
        try { return _meta.getDriverVersion(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getExportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getExportedKeys(catalog, schema, table));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        try { return _meta.getExtraNameCharacters(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        try { return _meta.getIdentifierQuoteString(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getImportedKeys(catalog, schema, table));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table,
            final boolean unique, final boolean approximate) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getIndexInfo(catalog, schema, table, unique,
                            approximate));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        try { return _meta.getJDBCMajorVersion(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        try { return _meta.getJDBCMinorVersion(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        try { return _meta.getMaxBinaryLiteralLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        try { return _meta.getMaxCatalogNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        try { return _meta.getMaxCharLiteralLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        try { return _meta.getMaxColumnNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        try { return _meta.getMaxColumnsInGroupBy(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        try { return _meta.getMaxColumnsInIndex(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        try { return _meta.getMaxColumnsInOrderBy(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        try { return _meta.getMaxColumnsInSelect(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        try { return _meta.getMaxColumnsInTable(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxConnections() throws SQLException {
        try { return _meta.getMaxConnections(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        try { return _meta.getMaxCursorNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        try { return _meta.getMaxIndexLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        try { return _meta.getMaxProcedureNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        try { return _meta.getMaxRowSize(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        try { return _meta.getMaxSchemaNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        try { return _meta.getMaxStatementLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxStatements() throws SQLException {
        try { return _meta.getMaxStatements(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        try { return _meta.getMaxTableNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        try { return _meta.getMaxTablesInSelect(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        try { return _meta.getMaxUserNameLength(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        try { return _meta.getNumericFunctions(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema, final String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getPrimaryKeys(catalog, schema, table));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getProcedureColumns(final String catalog, final String schemaPattern,
            final String procedureNamePattern, final String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getProcedureColumns(catalog, schemaPattern,
                            procedureNamePattern, columnNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        try { return _meta.getProcedureTerm(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern,
            final String procedureNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getProcedures(catalog, schemaPattern,
                            procedureNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        try { return _meta.getResultSetHoldability(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        try { return _meta.getSQLKeywords(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public int getSQLStateType() throws SQLException {
        try { return _meta.getSQLStateType(); }
        catch (final SQLException e) { handleException(e); return 0; }
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        try { return _meta.getSchemaTerm(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSchemas());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        try { return _meta.getSearchStringEscape(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getStringFunctions() throws SQLException {
        try { return _meta.getStringFunctions(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getSuperTables(final String catalog, final String schemaPattern,
            final String tableNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSuperTables(catalog, schemaPattern,
                            tableNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSuperTypes(final String catalog, final String schemaPattern,
            final String typeNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSuperTypes(catalog, schemaPattern,
                            typeNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        try { return _meta.getSystemFunctions(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getTablePrivileges(final String catalog, final String schemaPattern,
            final String tableNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTablePrivileges(catalog, schemaPattern,
                            tableNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTableTypes());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern,
            final String tableNamePattern, final String[] types) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTables(catalog, schemaPattern, tableNamePattern,
                            types));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        try { return _meta.getTimeDateFunctions(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTypeInfo());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getUDTs(final String catalog, final String schemaPattern,
            final String typeNamePattern, final int[] types) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getUDTs(catalog, schemaPattern, typeNamePattern,
                            types));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getURL() throws SQLException {
        try { return _meta.getURL(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public String getUserName() throws SQLException {
        try { return _meta.getUserName(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getVersionColumns(final String catalog, final String schema,
            final String table) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getVersionColumns(catalog, schema, table));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public boolean insertsAreDetected(final int type) throws SQLException {
        try { return _meta.insertsAreDetected(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        try { return _meta.isCatalogAtStart(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        try { return _meta.isReadOnly(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        try { return _meta.locatorsUpdateCopy(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        try { return _meta.nullPlusNonNullIsNull(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        try { return _meta.nullsAreSortedAtEnd(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        try { return _meta.nullsAreSortedAtStart(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        try { return _meta.nullsAreSortedHigh(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        try { return _meta.nullsAreSortedLow(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean othersDeletesAreVisible(final int type) throws SQLException {
        try { return _meta.othersDeletesAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean othersInsertsAreVisible(final int type) throws SQLException {
        try { return _meta.othersInsertsAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean othersUpdatesAreVisible(final int type) throws SQLException {
        try { return _meta.othersUpdatesAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean ownDeletesAreVisible(final int type) throws SQLException {
        try { return _meta.ownDeletesAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean ownInsertsAreVisible(final int type) throws SQLException {
        try { return _meta.ownInsertsAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean ownUpdatesAreVisible(final int type) throws SQLException {
        try { return _meta.ownUpdatesAreVisible(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        try { return _meta.storesLowerCaseIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        try { return _meta.storesLowerCaseQuotedIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        try { return _meta.storesMixedCaseIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        try { return _meta.storesMixedCaseQuotedIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        try { return _meta.storesUpperCaseIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        try { return _meta.storesUpperCaseQuotedIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        try { return _meta.supportsANSI92EntryLevelSQL(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        try { return _meta.supportsANSI92FullSQL(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        try { return _meta.supportsANSI92IntermediateSQL(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        try { return _meta.supportsAlterTableWithAddColumn(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        try { return _meta.supportsAlterTableWithDropColumn(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        try { return _meta.supportsBatchUpdates(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        try { return _meta.supportsCatalogsInDataManipulation(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        try { return _meta.supportsCatalogsInIndexDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        try { return _meta.supportsCatalogsInPrivilegeDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        try { return _meta.supportsCatalogsInProcedureCalls(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        try { return _meta.supportsCatalogsInTableDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        try { return _meta.supportsColumnAliasing(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        try { return _meta.supportsConvert(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsConvert(final int fromType, final int toType)
            throws SQLException {
        try { return _meta.supportsConvert(fromType, toType); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        try { return _meta.supportsCoreSQLGrammar(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        try { return _meta.supportsCorrelatedSubqueries(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions()
            throws SQLException {
        try { return _meta.supportsDataDefinitionAndDataManipulationTransactions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly()
            throws SQLException {
        try { return _meta.supportsDataManipulationTransactionsOnly(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        try { return _meta.supportsDifferentTableCorrelationNames(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        try { return _meta.supportsExpressionsInOrderBy(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        try { return _meta.supportsExtendedSQLGrammar(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        try { return _meta.supportsFullOuterJoins(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        try { return _meta.supportsGetGeneratedKeys(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        try { return _meta.supportsGroupBy(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        try { return _meta.supportsGroupByBeyondSelect(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        try { return _meta.supportsGroupByUnrelated(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        try { return _meta.supportsIntegrityEnhancementFacility(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        try { return _meta.supportsLikeEscapeClause(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        try { return _meta.supportsLimitedOuterJoins(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        try { return _meta.supportsMinimumSQLGrammar(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        try { return _meta.supportsMixedCaseIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        try { return _meta.supportsMixedCaseQuotedIdentifiers(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        try { return _meta.supportsMultipleOpenResults(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        try { return _meta.supportsMultipleResultSets(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        try { return _meta.supportsMultipleTransactions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        try { return _meta.supportsNamedParameters(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        try { return _meta.supportsNonNullableColumns(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        try { return _meta.supportsOpenCursorsAcrossCommit(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        try { return _meta.supportsOpenCursorsAcrossRollback(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        try { return _meta.supportsOpenStatementsAcrossCommit(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        try { return _meta.supportsOpenStatementsAcrossRollback(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        try { return _meta.supportsOrderByUnrelated(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        try { return _meta.supportsOuterJoins(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        try { return _meta.supportsPositionedDelete(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        try { return _meta.supportsPositionedUpdate(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency)
            throws SQLException {
        try { return _meta.supportsResultSetConcurrency(type, concurrency); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability)
            throws SQLException {
        try { return _meta.supportsResultSetHoldability(holdability); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        try { return _meta.supportsResultSetType(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        try { return _meta.supportsSavepoints(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        try { return _meta.supportsSchemasInDataManipulation(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        try { return _meta.supportsSchemasInIndexDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        try { return _meta.supportsSchemasInPrivilegeDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        try { return _meta.supportsSchemasInProcedureCalls(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        try { return _meta.supportsSchemasInTableDefinitions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        try { return _meta.supportsSelectForUpdate(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        try { return _meta.supportsStatementPooling(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        try { return _meta.supportsStoredProcedures(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        try { return _meta.supportsSubqueriesInComparisons(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        try { return _meta.supportsSubqueriesInExists(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        try { return _meta.supportsSubqueriesInIns(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        try { return _meta.supportsSubqueriesInQuantifieds(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        try { return _meta.supportsTableCorrelationNames(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsTransactionIsolationLevel(final int level)
            throws SQLException {
        try { return _meta.supportsTransactionIsolationLevel(level); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        try { return _meta.supportsTransactions(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        try { return _meta.supportsUnion(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        try { return _meta.supportsUnionAll(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean updatesAreDetected(final int type) throws SQLException {
        try { return _meta.updatesAreDetected(type); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        try { return _meta.usesLocalFilePerTable(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        try { return _meta.usesLocalFiles(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    /* JDBC_4_ANT_KEY_BEGIN */

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(_meta.getClass())) {
            return true;
        } else {
            return _meta.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(_meta.getClass())) {
            return iface.cast(_meta);
        } else {
            return _meta.unwrap(iface);
        }
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        try { return _meta.getRowIdLifetime(); }
        catch (final SQLException e) { handleException(e); throw new AssertionError(); }
    }

    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern)
    throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSchemas(catalog, schemaPattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        try { return _meta.autoCommitFailureClosesAllResultSets(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        try { return _meta.supportsStoredFunctionsUsingCallSyntax(); }
        catch (final SQLException e) { handleException(e); return false; }
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getClientInfoProperties());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctions(final String catalog, final String schemaPattern,
            final String functionNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getFunctions(catalog, schemaPattern,
                            functionNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctionColumns(final String catalog, final String schemaPattern,
            final String functionNamePattern, final String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getFunctionColumns(catalog, schemaPattern,
                            functionNamePattern, columnNamePattern));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    /* JDBC_4_ANT_KEY_END */

    @Override
    public ResultSet getPseudoColumns(final String catalog, final String schemaPattern,
            final String tableNamePattern, final String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getPseudoColumns(catalog, schemaPattern,
                            tableNamePattern, columnNamePattern));
}
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        _conn.checkOpen();
        try {
            return _meta.generatedKeyAlwaysReturned();
        }
        catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }
}
