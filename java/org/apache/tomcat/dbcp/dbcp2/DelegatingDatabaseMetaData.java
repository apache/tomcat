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
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * <p>
 * A base delegating implementation of {@link DatabaseMetaData}.
 * </p>
 * <p>
 * Methods that create {@link ResultSet} objects are wrapped to create {@link DelegatingResultSet} objects and the remaining methods simply call the
 * corresponding method on the "delegate" provided in the constructor.
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
     * @param connection       the delegating connection
     * @param databaseMetaData the database meta data
     */
    public DelegatingDatabaseMetaData(final DelegatingConnection<?> connection, final DatabaseMetaData databaseMetaData) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.databaseMetaData = Objects.requireNonNull(databaseMetaData, "databaseMetaData");
    }

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return getB(databaseMetaData::allProceduresAreCallable);
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return getB(databaseMetaData::allTablesAreSelectable);
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return getB(databaseMetaData::autoCommitFailureClosesAllResultSets);
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return getB(databaseMetaData::dataDefinitionCausesTransactionCommit);
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return getB(databaseMetaData::dataDefinitionIgnoredInTransactions);
    }

    @Override
    public boolean deletesAreDetected(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.deletesAreDetected(type)));
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return getB(databaseMetaData::doesMaxRowSizeIncludeBlobs);
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        connection.checkOpen();
        return getB(() -> Boolean.valueOf(Jdbc41Bridge.generatedKeyAlwaysReturned(databaseMetaData)));
    }

    private <T> T get(final Callable<T> s) throws SQLException {
        return get(s, null);
    }

    private <T> T get(final Callable<T> s, final T defaultValue) throws SQLException {
        try {
            return s.call();
        } catch (final Exception e) {
            if (e instanceof SQLException) {
                handleException((SQLException) e);
            }
            return defaultValue;
        }
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern, final String typeNamePattern, final String attributeNamePattern)
            throws SQLException {
        return getRS(() -> databaseMetaData.getAttributes(catalog, schemaPattern, typeNamePattern, attributeNamePattern));
    }

    private boolean getB(final Callable<Boolean> s) throws SQLException {
        return get(s, Boolean.FALSE).booleanValue();
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema, final String table, final int scope, final boolean nullable)
            throws SQLException {
        return getRS(() -> databaseMetaData.getBestRowIdentifier(catalog, schema, table, scope, nullable));
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        return getRS(databaseMetaData::getCatalogs);
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        return get(databaseMetaData::getCatalogSeparator);
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        return get(databaseMetaData::getCatalogTerm);
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        return getRS(databaseMetaData::getClientInfoProperties);
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema, final String table, final String columnNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getColumnPrivileges(catalog, schema, table, columnNamePattern));
    }

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern)
            throws SQLException {
        return getRS(() -> databaseMetaData.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public ResultSet getCrossReference(final String parentCatalog, final String parentSchema, final String parentTable, final String foreignCatalog,
            final String foreignSchema, final String foreignTable) throws SQLException {
        return getRS(() -> databaseMetaData.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable));
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        return getI(databaseMetaData::getDatabaseMajorVersion);
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        return getI(databaseMetaData::getDatabaseMinorVersion);
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return get(databaseMetaData::getDatabaseProductName);
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        return get(databaseMetaData::getDatabaseProductVersion);
    }

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        return getI(databaseMetaData::getDefaultTransactionIsolation);
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
        return get(databaseMetaData::getDriverName);
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return get(databaseMetaData::getDriverVersion);
    }

    @Override
    public ResultSet getExportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        return getRS(() -> databaseMetaData.getExportedKeys(catalog, schema, table));
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        return get(databaseMetaData::getExtraNameCharacters);
    }

    @Override
    public ResultSet getFunctionColumns(final String catalog, final String schemaPattern, final String functionNamePattern, final String columnNamePattern)
            throws SQLException {
        return getRS(() -> databaseMetaData.getFunctionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern));
    }

    @Override
    public ResultSet getFunctions(final String catalog, final String schemaPattern, final String functionNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getFunctions(catalog, schemaPattern, functionNamePattern));
    }

    private int getI(final Callable<Integer> s) throws SQLException {
        return get(s, Integer.valueOf(0)).intValue();
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        return get(databaseMetaData::getIdentifierQuoteString);
    }

    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema, final String table) throws SQLException {
        return getRS(() -> databaseMetaData.getImportedKeys(catalog, schema, table));
    }

    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table, final boolean unique, final boolean approximate)
            throws SQLException {
        return getRS(() -> databaseMetaData.getIndexInfo(catalog, schema, table, unique, approximate));
    }

    /**
     * If my underlying {@link ResultSet} is not a {@code DelegatingResultSet}, returns it, otherwise recursively invokes this method on my delegate.
     * <p>
     * Hence this method will return the first delegate that is not a {@code DelegatingResultSet}, or {@code null} when no non-{@code DelegatingResultSet}
     * delegate can be found by traversing this chain.
     * </p>
     * <p>
     * This method is useful when you may have nested {@code DelegatingResultSet}s, and you want to make sure to obtain a "genuine" {@link ResultSet}.
     * </p>
     *
     * @return the innermost database meta data.
     */
    public DatabaseMetaData getInnermostDelegate() {
        DatabaseMetaData m = databaseMetaData;
        while (m instanceof DelegatingDatabaseMetaData) {
            m = ((DelegatingDatabaseMetaData) m).getDelegate();
            if (this == m) {
                return null;
            }
        }
        return m;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        return getI(databaseMetaData::getJDBCMajorVersion);
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        return getI(databaseMetaData::getJDBCMinorVersion);
    }

    private long getL(final Callable<Long> s) throws SQLException {
        return get(s, Long.valueOf(0)).longValue();
    }

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        return getI(databaseMetaData::getMaxBinaryLiteralLength);
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxCatalogNameLength);
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return getI(databaseMetaData::getMaxCharLiteralLength);
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxColumnNameLength);
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        return getI(databaseMetaData::getMaxColumnsInGroupBy);
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return getI(databaseMetaData::getMaxColumnsInIndex);
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return getI(databaseMetaData::getMaxColumnsInOrderBy);
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return getI(databaseMetaData::getMaxColumnsInSelect);
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return getI(databaseMetaData::getMaxColumnsInTable);
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return getI(databaseMetaData::getMaxConnections);
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxCursorNameLength);
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return getI(databaseMetaData::getMaxIndexLength);
    }

    /**
     * @since 2.5.0
     */
    @Override
    public long getMaxLogicalLobSize() throws SQLException {
        return getL(databaseMetaData::getMaxLogicalLobSize);
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxProcedureNameLength);
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return getI(databaseMetaData::getMaxRowSize);
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxSchemaNameLength);
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return getI(databaseMetaData::getMaxStatementLength);
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return getI(databaseMetaData::getMaxStatements);
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxTableNameLength);
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return getI(databaseMetaData::getMaxTablesInSelect);
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return getI(databaseMetaData::getMaxUserNameLength);
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return get(databaseMetaData::getNumericFunctions);
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema, final String table) throws SQLException {
        return getRS(() -> databaseMetaData.getPrimaryKeys(catalog, schema, table));
    }

    @Override
    public ResultSet getProcedureColumns(final String catalog, final String schemaPattern, final String procedureNamePattern, final String columnNamePattern)
            throws SQLException {
        return getRS(() -> databaseMetaData.getProcedureColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern));
    }

    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern, final String procedureNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getProcedures(catalog, schemaPattern, procedureNamePattern));
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return get(databaseMetaData::getProcedureTerm);
    }

    @Override
    public ResultSet getPseudoColumns(final String catalog, final String schemaPattern, final String tableNamePattern, final String columnNamePattern)
            throws SQLException {
        return getRS(() -> Jdbc41Bridge.getPseudoColumns(databaseMetaData, catalog, schemaPattern, tableNamePattern, columnNamePattern));
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return getI(databaseMetaData::getResultSetHoldability);
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        return get(databaseMetaData::getRowIdLifetime);
    }

    private ResultSet getRS(final Callable<ResultSet> s) throws SQLException {
        connection.checkOpen();
        return DelegatingResultSet.wrapResultSet(connection, get(s));
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return getRS(databaseMetaData::getSchemas);
    }

    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern) throws SQLException {
        return getRS(() -> databaseMetaData.getSchemas(catalog, schemaPattern));
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        return get(databaseMetaData::getSchemaTerm);
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        return get(databaseMetaData::getSearchStringEscape);
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        return get(databaseMetaData::getSQLKeywords);
    }

    @Override
    public int getSQLStateType() throws SQLException {
        return getI(databaseMetaData::getSQLStateType);
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return get(databaseMetaData::getStringFunctions);
    }

    @Override
    public ResultSet getSuperTables(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getSuperTables(catalog, schemaPattern, tableNamePattern));
    }

    @Override
    public ResultSet getSuperTypes(final String catalog, final String schemaPattern, final String typeNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getSuperTypes(catalog, schemaPattern, typeNamePattern));
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return get(databaseMetaData::getSystemFunctions);
    }

    @Override
    public ResultSet getTablePrivileges(final String catalog, final String schemaPattern, final String tableNamePattern) throws SQLException {
        return getRS(() -> databaseMetaData.getTablePrivileges(catalog, schemaPattern, tableNamePattern));
    }

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern, final String tableNamePattern, final String[] types) throws SQLException {
        return getRS(() -> databaseMetaData.getTables(catalog, schemaPattern, tableNamePattern, types));
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        return getRS(databaseMetaData::getTableTypes);
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        return get(databaseMetaData::getTimeDateFunctions);
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        return getRS(databaseMetaData::getTypeInfo);
    }

    @Override
    public ResultSet getUDTs(final String catalog, final String schemaPattern, final String typeNamePattern, final int[] types) throws SQLException {
        return getRS(() -> databaseMetaData.getUDTs(catalog, schemaPattern, typeNamePattern, types));
    }

    @Override
    public String getURL() throws SQLException {
        return get(databaseMetaData::getURL);
    }

    @Override
    public String getUserName() throws SQLException {
        return get(databaseMetaData::getUserName);
    }

    @Override
    public ResultSet getVersionColumns(final String catalog, final String schema, final String table) throws SQLException {
        return getRS(() -> databaseMetaData.getVersionColumns(catalog, schema, table));
    }

    /**
     * Delegates to the connection's {@link DelegatingConnection#handleException(SQLException)}.
     *
     * @param e the exception to throw or delegate.
     * @throws SQLException the exception to throw.
     */
    protected void handleException(final SQLException e) throws SQLException {
        if (connection == null) {
            throw e;
        }
        connection.handleException(e);
    }

    @Override
    public boolean insertsAreDetected(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.insertsAreDetected(type)));
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return getB(databaseMetaData::isCatalogAtStart);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return getB(databaseMetaData::isReadOnly);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        }
        if (iface.isAssignableFrom(databaseMetaData.getClass())) {
            return true;
        }
        return databaseMetaData.isWrapperFor(iface);
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        return getB(databaseMetaData::locatorsUpdateCopy);
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        return getB(databaseMetaData::nullPlusNonNullIsNull);
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        return getB(databaseMetaData::nullsAreSortedAtEnd);
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        return getB(databaseMetaData::nullsAreSortedAtStart);
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        return getB(databaseMetaData::nullsAreSortedHigh);
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return getB(databaseMetaData::nullsAreSortedLow);
    }

    @Override
    public boolean othersDeletesAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.othersDeletesAreVisible(type)));
    }

    @Override
    public boolean othersInsertsAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.othersInsertsAreVisible(type)));
    }

    @Override
    public boolean othersUpdatesAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.othersUpdatesAreVisible(type)));
    }

    @Override
    public boolean ownDeletesAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.ownDeletesAreVisible(type)));
    }

    @Override
    public boolean ownInsertsAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.ownInsertsAreVisible(type)));
    }

    @Override
    public boolean ownUpdatesAreVisible(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.ownUpdatesAreVisible(type)));
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesLowerCaseIdentifiers);
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesLowerCaseQuotedIdentifiers);
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesMixedCaseIdentifiers);
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesMixedCaseQuotedIdentifiers);
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesUpperCaseIdentifiers);

    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return getB(databaseMetaData::storesUpperCaseQuotedIdentifiers);
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return getB(databaseMetaData::supportsAlterTableWithAddColumn);
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return getB(databaseMetaData::supportsAlterTableWithDropColumn);
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return getB(databaseMetaData::supportsANSI92EntryLevelSQL);
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        return getB(databaseMetaData::supportsANSI92FullSQL);
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return getB(databaseMetaData::supportsANSI92IntermediateSQL);
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        return getB(databaseMetaData::supportsBatchUpdates);
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return getB(databaseMetaData::supportsCatalogsInDataManipulation);
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsCatalogsInIndexDefinitions);
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsCatalogsInPrivilegeDefinitions);
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return getB(databaseMetaData::supportsCatalogsInProcedureCalls);
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsCatalogsInTableDefinitions);
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        return getB(databaseMetaData::supportsColumnAliasing);
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        return getB(databaseMetaData::supportsConvert);
    }

    @Override
    public boolean supportsConvert(final int fromType, final int toType) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.supportsConvert(fromType, toType)));
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return getB(databaseMetaData::supportsCoreSQLGrammar);
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return getB(databaseMetaData::supportsCorrelatedSubqueries);
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return getB(databaseMetaData::supportsDataDefinitionAndDataManipulationTransactions);
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return getB(databaseMetaData::supportsDataManipulationTransactionsOnly);
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return getB(databaseMetaData::supportsDifferentTableCorrelationNames);
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return getB(databaseMetaData::supportsExpressionsInOrderBy);
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return getB(databaseMetaData::supportsExtendedSQLGrammar);
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        return getB(databaseMetaData::supportsFullOuterJoins);
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        return getB(databaseMetaData::supportsGetGeneratedKeys);
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        return getB(databaseMetaData::supportsGroupBy);
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return getB(databaseMetaData::supportsGroupByBeyondSelect);
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        return getB(databaseMetaData::supportsGroupByUnrelated);
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return getB(databaseMetaData::supportsIntegrityEnhancementFacility);
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        return getB(databaseMetaData::supportsLikeEscapeClause);
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        return getB(databaseMetaData::supportsLimitedOuterJoins);
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        return getB(databaseMetaData::supportsMinimumSQLGrammar);
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return getB(databaseMetaData::supportsMixedCaseIdentifiers);
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return getB(databaseMetaData::supportsMixedCaseQuotedIdentifiers);
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        return getB(databaseMetaData::supportsMultipleOpenResults);
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        return getB(databaseMetaData::supportsMultipleResultSets);
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        return getB(databaseMetaData::supportsMultipleTransactions);
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        return getB(databaseMetaData::supportsNamedParameters);
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        return getB(databaseMetaData::supportsNonNullableColumns);
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return getB(databaseMetaData::supportsOpenCursorsAcrossCommit);
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return getB(databaseMetaData::supportsOpenCursorsAcrossRollback);
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return getB(databaseMetaData::supportsOpenStatementsAcrossCommit);
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return getB(databaseMetaData::supportsOpenStatementsAcrossRollback);
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        return getB(databaseMetaData::supportsOrderByUnrelated);
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        return getB(databaseMetaData::supportsOuterJoins);
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        return getB(databaseMetaData::supportsPositionedDelete);
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        return getB(databaseMetaData::supportsPositionedUpdate);
    }

    /**
     * @since 2.5.0
     */
    @Override
    public boolean supportsRefCursors() throws SQLException {
        return getB(databaseMetaData::supportsRefCursors);
    }

    @Override
    public boolean supportsResultSetConcurrency(final int type, final int concurrency) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.supportsResultSetConcurrency(type, concurrency)));
    }

    @Override
    public boolean supportsResultSetHoldability(final int holdability) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.supportsResultSetHoldability(holdability)));
    }

    @Override
    public boolean supportsResultSetType(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.supportsResultSetType(type)));
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return getB(databaseMetaData::supportsSavepoints);
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return getB(databaseMetaData::supportsSchemasInDataManipulation);
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsSchemasInIndexDefinitions);
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsSchemasInPrivilegeDefinitions);
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return getB(databaseMetaData::supportsSchemasInProcedureCalls);
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return getB(databaseMetaData::supportsSchemasInTableDefinitions);
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        return getB(databaseMetaData::supportsSelectForUpdate);
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        return getB(databaseMetaData::supportsStatementPooling);
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return getB(databaseMetaData::supportsStoredFunctionsUsingCallSyntax);
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        return getB(databaseMetaData::supportsStoredProcedures);
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        return getB(databaseMetaData::supportsSubqueriesInComparisons);
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        return getB(databaseMetaData::supportsSubqueriesInExists);
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        return getB(databaseMetaData::supportsSubqueriesInIns);
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return getB(databaseMetaData::supportsSubqueriesInQuantifieds);
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        return getB(databaseMetaData::supportsTableCorrelationNames);
    }

    @Override
    public boolean supportsTransactionIsolationLevel(final int level) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.supportsTransactionIsolationLevel(level)));
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return getB(databaseMetaData::supportsTransactions);
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        return getB(databaseMetaData::supportsUnion);
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        return getB(databaseMetaData::supportsUnionAll);
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        if (iface.isAssignableFrom(databaseMetaData.getClass())) {
            return iface.cast(databaseMetaData);
        }
        return databaseMetaData.unwrap(iface);
    }

    @Override
    public boolean updatesAreDetected(final int type) throws SQLException {
        return getB(() -> Boolean.valueOf(databaseMetaData.updatesAreDetected(type)));
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        return getB(databaseMetaData::usesLocalFilePerTable);
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        return getB(databaseMetaData::usesLocalFiles);
    }
}
