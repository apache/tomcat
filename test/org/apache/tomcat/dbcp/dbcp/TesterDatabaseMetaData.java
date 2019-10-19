/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.commons.dbcp;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
/* JDBC_4_ANT_KEY_BEGIN */
import java.sql.RowIdLifetime;
/* JDBC_4_ANT_KEY_END */
import java.sql.SQLException;

/**
 * Dummy {@link DatabaseMetaData} for tetsing purposes. Implements only those
 * methods required by the test cases.
 */
public class TesterDatabaseMetaData implements DatabaseMetaData {

    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    public boolean allTablesAreSelectable() throws SQLException {
        return false;
    }

    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return false;
    }

    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    public boolean deletesAreDetected(int type) throws SQLException {
        return false;
    }

    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return false;
    }

    public ResultSet getAttributes(String catalog, String schemaPattern,
            String typeNamePattern, String attributeNamePattern)
            throws SQLException {
        return null;
    }

    public ResultSet getBestRowIdentifier(String catalog, String schema,
            String table, int scope, boolean nullable) throws SQLException {
        return null;
    }

    public String getCatalogSeparator() throws SQLException {
        return null;
    }

    public String getCatalogTerm() throws SQLException {
        return null;
    }

    public ResultSet getCatalogs() throws SQLException {
        return null;
    }

    public ResultSet getColumnPrivileges(String catalog, String schema,
            String table, String columnNamePattern) throws SQLException {
        return null;
    }

    public ResultSet getColumns(String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern)
            throws SQLException {
        return null;
    }

    public Connection getConnection() throws SQLException {
        return null;
    }

    public ResultSet getCrossReference(String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog,
            String foreignSchema, String foreignTable) throws SQLException {
        return null;
    }

    public int getDatabaseMajorVersion() throws SQLException {
        return 0;
    }

    public int getDatabaseMinorVersion() throws SQLException {
        return 0;
    }

    public String getDatabaseProductName() throws SQLException {
        return null;
    }

    public String getDatabaseProductVersion() throws SQLException {
        return null;
    }

    public int getDefaultTransactionIsolation() throws SQLException {
        return 0;
    }

    public int getDriverMajorVersion() {
        return 0;
    }

    public int getDriverMinorVersion() {
        return 0;
    }

    public String getDriverName() throws SQLException {
        return null;
    }

    public String getDriverVersion() throws SQLException {
        return null;
    }

    public ResultSet getExportedKeys(String catalog, String schema, String table)
            throws SQLException {
        return null;
    }

    public String getExtraNameCharacters() throws SQLException {
        return null;
    }

    public String getIdentifierQuoteString() throws SQLException {
        return null;
    }

    public ResultSet getImportedKeys(String catalog, String schema, String table)
            throws SQLException {
        return null;
    }

    public ResultSet getIndexInfo(String catalog, String schema, String table,
            boolean unique, boolean approximate) throws SQLException {
        return null;
    }

    public int getJDBCMajorVersion() throws SQLException {
        return 0;
    }

    public int getJDBCMinorVersion() throws SQLException {
        return 0;
    }

    public int getMaxBinaryLiteralLength() throws SQLException {
        return 0;
    }

    public int getMaxCatalogNameLength() throws SQLException {
        return 0;
    }

    public int getMaxCharLiteralLength() throws SQLException {
        return 0;
    }

    public int getMaxColumnNameLength() throws SQLException {
        return 0;
    }

    public int getMaxColumnsInGroupBy() throws SQLException {
        return 0;
    }

    public int getMaxColumnsInIndex() throws SQLException {
        return 0;
    }

    public int getMaxColumnsInOrderBy() throws SQLException {
        return 0;
    }

    public int getMaxColumnsInSelect() throws SQLException {
        return 0;
    }

    public int getMaxColumnsInTable() throws SQLException {
        return 0;
    }

    public int getMaxConnections() throws SQLException {
        return 0;
    }

    public int getMaxCursorNameLength() throws SQLException {
        return 0;
    }

    public int getMaxIndexLength() throws SQLException {
        return 0;
    }

    public int getMaxProcedureNameLength() throws SQLException {
        return 0;
    }

    public int getMaxRowSize() throws SQLException {
        return 0;
    }

    public int getMaxSchemaNameLength() throws SQLException {
        return 0;
    }

    public int getMaxStatementLength() throws SQLException {
        return 0;
    }

    public int getMaxStatements() throws SQLException {
        return 0;
    }

    public int getMaxTableNameLength() throws SQLException {
        return 0;
    }

    public int getMaxTablesInSelect() throws SQLException {
        return 0;
    }

    public int getMaxUserNameLength() throws SQLException {
        return 0;
    }

    public String getNumericFunctions() throws SQLException {
        return null;
    }

    public ResultSet getPrimaryKeys(String catalog, String schema, String table)
            throws SQLException {
        return null;
    }

    public ResultSet getProcedureColumns(String catalog, String schemaPattern,
            String procedureNamePattern, String columnNamePattern)
            throws SQLException {
        return null;
    }

    public String getProcedureTerm() throws SQLException {
        return null;
    }

    public ResultSet getProcedures(String catalog, String schemaPattern,
            String procedureNamePattern) throws SQLException {
        return null;
    }

    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    public String getSQLKeywords() throws SQLException {
        return null;
    }

    public int getSQLStateType() throws SQLException {
        return 0;
    }

    public String getSchemaTerm() throws SQLException {
        return null;
    }

    public ResultSet getSchemas() throws SQLException {
        return new TesterResultSet(null);
    }

    public String getSearchStringEscape() throws SQLException {
        return null;
    }

    public String getStringFunctions() throws SQLException {
        return null;
    }

    public ResultSet getSuperTables(String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        return null;
    }

    public ResultSet getSuperTypes(String catalog, String schemaPattern,
            String typeNamePattern) throws SQLException {
        return null;
    }

    public String getSystemFunctions() throws SQLException {
        return null;
    }

    public ResultSet getTablePrivileges(String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        return null;
    }

    public ResultSet getTableTypes() throws SQLException {
        return null;
    }

    public ResultSet getTables(String catalog, String schemaPattern,
            String tableNamePattern, String[] types) throws SQLException {
        return null;
    }

    public String getTimeDateFunctions() throws SQLException {
        return null;
    }

    public ResultSet getTypeInfo() throws SQLException {
        return null;
    }

    public ResultSet getUDTs(String catalog, String schemaPattern,
            String typeNamePattern, int[] types) throws SQLException {
        return null;
    }

    public String getURL() throws SQLException {
        return null;
    }

    public String getUserName() throws SQLException {
        return null;
    }

    public ResultSet getVersionColumns(String catalog, String schema,
            String table) throws SQLException {
        return null;
    }

    public boolean insertsAreDetected(int type) throws SQLException {
        return false;
    }

    public boolean isCatalogAtStart() throws SQLException {
        return false;
    }

    public boolean isReadOnly() throws SQLException {
        return false;
    }

    public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    public boolean nullPlusNonNullIsNull() throws SQLException {
        return false;
    }

    public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    public boolean nullsAreSortedAtStart() throws SQLException {
        return false;
    }

    public boolean nullsAreSortedHigh() throws SQLException {
        return false;
    }

    public boolean nullsAreSortedLow() throws SQLException {
        return false;
    }

    public boolean othersDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean othersInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean ownDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean ownInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    public boolean storesLowerCaseIdentifiers() throws SQLException {
        return false;
    }

    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    public boolean storesMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    public boolean storesUpperCaseIdentifiers() throws SQLException {
        return false;
    }

    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return false;
    }

    public boolean supportsANSI92FullSQL() throws SQLException {
        return false;
    }

    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return false;
    }

    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return false;
    }

    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return false;
    }

    public boolean supportsBatchUpdates() throws SQLException {
        return false;
    }

    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return false;
    }

    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return false;
    }

    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsColumnAliasing() throws SQLException {
        return false;
    }

    public boolean supportsConvert() throws SQLException {
        return false;
    }

    public boolean supportsConvert(int fromType, int toType)
            throws SQLException {
        return false;
    }

    public boolean supportsCoreSQLGrammar() throws SQLException {
        return false;
    }

    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return false;
    }

    public boolean supportsDataDefinitionAndDataManipulationTransactions()
            throws SQLException {
        return false;
    }

    public boolean supportsDataManipulationTransactionsOnly()
            throws SQLException {
        return false;
    }

    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return false;
    }

    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return false;
    }

    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    public boolean supportsFullOuterJoins() throws SQLException {
        return false;
    }

    public boolean supportsGetGeneratedKeys() throws SQLException {
        return false;
    }

    public boolean supportsGroupBy() throws SQLException {
        return false;
    }

    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return false;
    }

    public boolean supportsGroupByUnrelated() throws SQLException {
        return false;
    }

    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return false;
    }

    public boolean supportsLikeEscapeClause() throws SQLException {
        return false;
    }

    public boolean supportsLimitedOuterJoins() throws SQLException {
        return false;
    }

    public boolean supportsMinimumSQLGrammar() throws SQLException {
        return false;
    }

    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    public boolean supportsMultipleOpenResults() throws SQLException {
        return false;
    }

    public boolean supportsMultipleResultSets() throws SQLException {
        return false;
    }

    public boolean supportsMultipleTransactions() throws SQLException {
        return false;
    }

    public boolean supportsNamedParameters() throws SQLException {
        return false;
    }

    public boolean supportsNonNullableColumns() throws SQLException {
        return false;
    }

    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return false;
    }

    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return false;
    }

    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return false;
    }

    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return false;
    }

    public boolean supportsOrderByUnrelated() throws SQLException {
        return false;
    }

    public boolean supportsOuterJoins() throws SQLException {
        return false;
    }

    public boolean supportsPositionedDelete() throws SQLException {
        return false;
    }

    public boolean supportsPositionedUpdate() throws SQLException {
        return false;
    }

    public boolean supportsResultSetConcurrency(int type, int concurrency)
            throws SQLException {
        return false;
    }

    public boolean supportsResultSetHoldability(int holdability)
            throws SQLException {
        return false;
    }

    public boolean supportsResultSetType(int type) throws SQLException {
        return false;
    }

    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return false;
    }

    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return false;
    }

    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return false;
    }

    public boolean supportsSelectForUpdate() throws SQLException {
        return false;
    }

    public boolean supportsStatementPooling() throws SQLException {
        return false;
    }

    public boolean supportsStoredProcedures() throws SQLException {
        return false;
    }

    public boolean supportsSubqueriesInComparisons() throws SQLException {
        return false;
    }

    public boolean supportsSubqueriesInExists() throws SQLException {
        return false;
    }

    public boolean supportsSubqueriesInIns() throws SQLException {
        return false;
    }

    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return false;
    }

    public boolean supportsTableCorrelationNames() throws SQLException {
        return false;
    }

    public boolean supportsTransactionIsolationLevel(int level)
            throws SQLException {
        return false;
    }

    public boolean supportsTransactions() throws SQLException {
        return false;
    }

    public boolean supportsUnion() throws SQLException {
        return false;
    }

    public boolean supportsUnionAll() throws SQLException {
        return false;
    }

    public boolean updatesAreDetected(int type) throws SQLException {
        return false;
    }

    public boolean usesLocalFilePerTable() throws SQLException {
        return false;
    }

    public boolean usesLocalFiles() throws SQLException {
        return false;
    }

    /* JDBC_4_ANT_KEY_BEGIN */

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public RowIdLifetime getRowIdLifetime() throws SQLException {
        return null;
    }

    public ResultSet getSchemas(String catalog, String schemaPattern)
    throws SQLException {
        return null;
    }

    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return false;
    }

    public ResultSet getClientInfoProperties() throws SQLException {
        return null;
    }

    public ResultSet getFunctionColumns(String catalog, String schemaPattern,
            String functionNamePattern, String columnNamePattern)
            throws SQLException {
        return null;
    }

    public ResultSet getFunctions(String catalog, String schemaPattern,
            String functionNamePattern) throws SQLException {
        return null;
    }
    /* JDBC_4_ANT_KEY_END */

    /* JDBC_4_1_ANT_KEY_BEGIN */
    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern)
            throws SQLException {
        return null;
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return false;
    }
    /* JDBC_4_1_ANT_KEY_END */
}
