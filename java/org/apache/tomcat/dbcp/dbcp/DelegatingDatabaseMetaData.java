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

package org.apache.tomcat.dbcp.dbcp;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;

import org.apache.tomcat.util.compat.JreCompat;

/**
 * <p>A base delegating implementation of {@link DatabaseMetaData}.</p>
 *
 * <p>Methods that create {@link ResultSet} objects are wrapped to
 * create {@link DelegatingResultSet} objects and the remaining methods
 * simply call the corresponding method on the "delegate"
 * provided in the constructor.</p>
 *
 * <p>NOTE: as of version 2.0, this class will no longer extend AbandonedTrace.</p>
 */
public class DelegatingDatabaseMetaData extends AbandonedTrace
        implements DatabaseMetaData {

    /** My delegate {@link DatabaseMetaData} */
    protected DatabaseMetaData _meta;

    /** The connection that created me. **/
    protected DelegatingConnection _conn = null;

    public DelegatingDatabaseMetaData(DelegatingConnection c,
            DatabaseMetaData m) {
        super();
        _conn = c;
        _meta = m;
    }

    public DatabaseMetaData getDelegate() {
        return _meta;
    }

    @Override
    public boolean equals(Object obj) {
    	if (this == obj) return true;
        DatabaseMetaData delegate = getInnermostDelegate();
        if (delegate == null) {
            return false;
        }
        if (obj instanceof DelegatingDatabaseMetaData) {
            DelegatingDatabaseMetaData s = (DelegatingDatabaseMetaData) obj;
            return delegate.equals(s.getInnermostDelegate());
        }
        else {
            return delegate.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        Object obj = getInnermostDelegate();
        if (obj == null) {
            return 0;
        }
        return obj.hashCode();
    }

    /**
     * If my underlying {@link ResultSet} is not a
     * <code>DelegatingResultSet</code>, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a <code>DelegatingResultSet</code>,
     * or <code>null</code> when no non-<code>DelegatingResultSet</code>
     * delegate can be found by transversing this chain.
     * <p>
     * This method is useful when you may have nested
     * <code>DelegatingResultSet</code>s, and you want to make
     * sure to obtain a "genuine" {@link ResultSet}.
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

    protected void handleException(SQLException e) throws SQLException {
        if (_conn != null) {
            _conn.handleException(e);
        }
        else {
            throw e;
        }
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        { try { return _meta.allProceduresAreCallable(); }
          catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        { try { return _meta.allTablesAreSelectable(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        { try { return _meta.dataDefinitionCausesTransactionCommit(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        { try { return _meta.dataDefinitionIgnoredInTransactions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        { try { return _meta.deletesAreDetected(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        { try { return _meta.doesMaxRowSizeIncludeBlobs(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern,
            String typeNamePattern, String attributeNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,_meta.getAttributes(
                    catalog, schemaPattern, typeNamePattern,
                    attributeNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema,
            String table, int scope, boolean nullable) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getBestRowIdentifier(catalog, schema, table, scope,
                            nullable));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        { try { return _meta.getCatalogSeparator(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        { try { return _meta.getCatalogTerm(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getCatalogs());
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema,
            String table, String columnNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getColumnPrivileges(catalog, schema, table,
                            columnNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getColumns(catalog, schemaPattern, tableNamePattern,
                            columnNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return _conn;
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog,
            String foreignSchema, String foreignTable) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getCrossReference(parentCatalog, parentSchema,
                            parentTable, foreignCatalog, foreignSchema,
                            foreignTable));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        { try { return _meta.getDatabaseMajorVersion(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        { try { return _meta.getDatabaseMinorVersion(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        { try { return _meta.getDatabaseProductName(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        { try { return _meta.getDatabaseProductVersion(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        { try { return _meta.getDefaultTransactionIsolation(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getDriverMajorVersion() {return _meta.getDriverMajorVersion();}

    @Override
    public int getDriverMinorVersion() {return _meta.getDriverMinorVersion();}

    @Override
    public String getDriverName() throws SQLException {
        { try { return _meta.getDriverName(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getDriverVersion() throws SQLException {
        { try { return _meta.getDriverVersion(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getExportedKeys(catalog, schema, table));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        { try { return _meta.getExtraNameCharacters(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        { try { return _meta.getIdentifierQuoteString(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getImportedKeys(catalog, schema, table));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table,
            boolean unique, boolean approximate) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getIndexInfo(catalog, schema, table, unique,
                            approximate));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        { try { return _meta.getJDBCMajorVersion(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        { try { return _meta.getJDBCMinorVersion(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        { try { return _meta.getMaxBinaryLiteralLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        { try { return _meta.getMaxCatalogNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        { try { return _meta.getMaxCharLiteralLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        { try { return _meta.getMaxColumnNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        { try { return _meta.getMaxColumnsInGroupBy(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        { try { return _meta.getMaxColumnsInIndex(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        { try { return _meta.getMaxColumnsInOrderBy(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        { try { return _meta.getMaxColumnsInSelect(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        { try { return _meta.getMaxColumnsInTable(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxConnections() throws SQLException {
        { try { return _meta.getMaxConnections(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        { try { return _meta.getMaxCursorNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        { try { return _meta.getMaxIndexLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        { try { return _meta.getMaxProcedureNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        { try { return _meta.getMaxRowSize(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        { try { return _meta.getMaxSchemaNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        { try { return _meta.getMaxStatementLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxStatements() throws SQLException {
        { try { return _meta.getMaxStatements(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        { try { return _meta.getMaxTableNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        { try { return _meta.getMaxTablesInSelect(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        { try { return _meta.getMaxUserNameLength(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        { try { return _meta.getNumericFunctions(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getPrimaryKeys(catalog, schema, table));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern,
            String procedureNamePattern, String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getProcedureColumns(catalog, schemaPattern,
                            procedureNamePattern, columnNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        { try { return _meta.getProcedureTerm(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern,
            String procedureNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getProcedures(catalog, schemaPattern,
                            procedureNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        { try { return _meta.getResultSetHoldability(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        { try { return _meta.getSQLKeywords(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public int getSQLStateType() throws SQLException {
        { try { return _meta.getSQLStateType(); }
        catch (SQLException e) { handleException(e); return 0; } }
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        { try { return _meta.getSchemaTerm(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSchemas());
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        { try { return _meta.getSearchStringEscape(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getStringFunctions() throws SQLException {
        { try { return _meta.getStringFunctions(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSuperTables(catalog, schemaPattern,
                            tableNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern,
            String typeNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSuperTypes(catalog, schemaPattern,
                            typeNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        { try { return _meta.getSystemFunctions(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTablePrivileges(catalog, schemaPattern,
                            tableNamePattern));
        }
        catch (SQLException e) {
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
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern,
            String tableNamePattern, String[] types) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTables(catalog, schemaPattern, tableNamePattern,
                            types));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        { try { return _meta.getTimeDateFunctions(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getTypeInfo());
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern,
            String typeNamePattern, int[] types) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getUDTs(catalog, schemaPattern, typeNamePattern,
                            types));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String getURL() throws SQLException {
        { try { return _meta.getURL(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public String getUserName() throws SQLException {
        { try { return _meta.getUserName(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema,
            String table) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getVersionColumns(catalog, schema, table));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        { try { return _meta.insertsAreDetected(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        { try { return _meta.isCatalogAtStart(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        { try { return _meta.isReadOnly(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        { try { return _meta.locatorsUpdateCopy(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        { try { return _meta.nullPlusNonNullIsNull(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        { try { return _meta.nullsAreSortedAtEnd(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        { try { return _meta.nullsAreSortedAtStart(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        { try { return _meta.nullsAreSortedHigh(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        { try { return _meta.nullsAreSortedLow(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
        { try { return _meta.othersDeletesAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
        { try { return _meta.othersInsertsAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        { try { return _meta.othersUpdatesAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
        { try { return _meta.ownDeletesAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
        { try { return _meta.ownInsertsAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        { try { return _meta.ownUpdatesAreVisible(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        { try { return _meta.storesLowerCaseIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        { try { return _meta.storesLowerCaseQuotedIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        { try { return _meta.storesMixedCaseIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        { try { return _meta.storesMixedCaseQuotedIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        { try { return _meta.storesUpperCaseIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        { try { return _meta.storesUpperCaseQuotedIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        { try { return _meta.supportsANSI92EntryLevelSQL(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        { try { return _meta.supportsANSI92FullSQL(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        { try { return _meta.supportsANSI92IntermediateSQL(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        { try { return _meta.supportsAlterTableWithAddColumn(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        { try { return _meta.supportsAlterTableWithDropColumn(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        { try { return _meta.supportsBatchUpdates(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        { try { return _meta.supportsCatalogsInDataManipulation(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        { try { return _meta.supportsCatalogsInIndexDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        { try { return _meta.supportsCatalogsInPrivilegeDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        { try { return _meta.supportsCatalogsInProcedureCalls(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        { try { return _meta.supportsCatalogsInTableDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        { try { return _meta.supportsColumnAliasing(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        { try { return _meta.supportsConvert(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsConvert(int fromType, int toType)
            throws SQLException {
        { try { return _meta.supportsConvert(fromType, toType); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        { try { return _meta.supportsCoreSQLGrammar(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        { try { return _meta.supportsCorrelatedSubqueries(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions()
            throws SQLException {
        { try { return _meta.supportsDataDefinitionAndDataManipulationTransactions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly()
            throws SQLException {
        { try { return _meta.supportsDataManipulationTransactionsOnly(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        { try { return _meta.supportsDifferentTableCorrelationNames(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        { try { return _meta.supportsExpressionsInOrderBy(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        { try { return _meta.supportsExtendedSQLGrammar(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        { try { return _meta.supportsFullOuterJoins(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        { try { return _meta.supportsGetGeneratedKeys(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        { try { return _meta.supportsGroupBy(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        { try { return _meta.supportsGroupByBeyondSelect(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        { try { return _meta.supportsGroupByUnrelated(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        { try { return _meta.supportsIntegrityEnhancementFacility(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        { try { return _meta.supportsLikeEscapeClause(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        { try { return _meta.supportsLimitedOuterJoins(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        { try { return _meta.supportsMinimumSQLGrammar(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        { try { return _meta.supportsMixedCaseIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        { try { return _meta.supportsMixedCaseQuotedIdentifiers(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        { try { return _meta.supportsMultipleOpenResults(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        { try { return _meta.supportsMultipleResultSets(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        { try { return _meta.supportsMultipleTransactions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        { try { return _meta.supportsNamedParameters(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        { try { return _meta.supportsNonNullableColumns(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        { try { return _meta.supportsOpenCursorsAcrossCommit(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        { try { return _meta.supportsOpenCursorsAcrossRollback(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        { try { return _meta.supportsOpenStatementsAcrossCommit(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        { try { return _meta.supportsOpenStatementsAcrossRollback(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        { try { return _meta.supportsOrderByUnrelated(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        { try { return _meta.supportsOuterJoins(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        { try { return _meta.supportsPositionedDelete(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        { try { return _meta.supportsPositionedUpdate(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency)
            throws SQLException {
        { try { return _meta.supportsResultSetConcurrency(type, concurrency); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability)
            throws SQLException {
        { try { return _meta.supportsResultSetHoldability(holdability); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        { try { return _meta.supportsResultSetType(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        { try { return _meta.supportsSavepoints(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        { try { return _meta.supportsSchemasInDataManipulation(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        { try { return _meta.supportsSchemasInIndexDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        { try { return _meta.supportsSchemasInPrivilegeDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        { try { return _meta.supportsSchemasInProcedureCalls(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        { try { return _meta.supportsSchemasInTableDefinitions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        { try { return _meta.supportsSelectForUpdate(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        { try { return _meta.supportsStatementPooling(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        { try { return _meta.supportsStoredProcedures(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        { try { return _meta.supportsSubqueriesInComparisons(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        { try { return _meta.supportsSubqueriesInExists(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        { try { return _meta.supportsSubqueriesInIns(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        { try { return _meta.supportsSubqueriesInQuantifieds(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        { try { return _meta.supportsTableCorrelationNames(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level)
            throws SQLException {
        { try { return _meta.supportsTransactionIsolationLevel(level); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        { try { return _meta.supportsTransactions(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        { try { return _meta.supportsUnion(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        { try { return _meta.supportsUnionAll(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        { try { return _meta.updatesAreDetected(type); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        { try { return _meta.usesLocalFilePerTable(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        { try { return _meta.usesLocalFiles(); }
        catch (SQLException e) { handleException(e); return false; } }
    }


    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass()) || _meta.isWrapperFor(iface);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
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
        { try { return _meta.getRowIdLifetime(); }
        catch (SQLException e) { handleException(e); throw new AssertionError(); } }
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern)
    throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getSchemas(catalog, schemaPattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        { try { return _meta.autoCommitFailureClosesAllResultSets(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        { try { return _meta.supportsStoredFunctionsUsingCallSyntax(); }
        catch (SQLException e) { handleException(e); return false; } }
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getClientInfoProperties());
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern,
            String functionNamePattern) throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getFunctions(catalog, schemaPattern,
                            functionNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern,
            String functionNamePattern, String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(_conn,
                    _meta.getFunctionColumns(catalog, schemaPattern,
                            functionNamePattern, columnNamePattern));
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }


    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public ResultSet getPseudoColumns(String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern)
            throws SQLException {
        _conn.checkOpen();
        try {
            return JreCompat.getInstance().getPseudoColumns(_meta,
                    catalog, schemaPattern, tableNamePattern, columnNamePattern);
        } catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    // No @Override else it won't compile with Java 6
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        _conn.checkOpen();
        try {
            return JreCompat.getInstance().generatedKeyAlwaysReturned(_meta);
        } catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }
    /* JDBC_4_1_ANT_KEY_END */
}
