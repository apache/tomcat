/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Report all types of query executions with actual parameters.
 *
 * <p> This interceptor provides detailed reporting(logging) of executed queries.
 *
 * <p> Report includes:
 * <ul>
 * <li>query execution status (success or failure)
 * <li>query execution time
 * <li>statement type
 * <li>batch execution
 * <li>num of batch
 * <li>num of query
 * <li>query
 * <li>query parameter values
 * </ul>
 *
 * <p> Properties:
 * <ul>
 * <li><em>logLevel:</em> JULI log level or system.out
 * <li><em>prefix, suffix:</em> prefix, suffix to the output
 * <li><em>reportAsJson:</em> output as json (boolean)
 * <li><em>showParams:</em> show/hide query parameters (boolean)
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptor extends AbstractCreateStatementInterceptor {

    protected static final Log log = LogFactory.getLog(QueryExecutionReportInterceptor.class);

    protected static final String ADD_BATCH = "addBatch";
    protected static final String CLEAR_BATCH = "clearBatch";
    protected static final String[] BATCH_PARAM_TYPES = {ADD_BATCH, CLEAR_BATCH};

    protected static final String SET_ARRAY = "setArray";
    protected static final String SET_ASCIISTREAM = "setAsciiStream";
    protected static final String SET_BIGDECIMAL = "setBigDecimal";
    protected static final String SET_BINARYSTREAM = "setBinaryStream";
    protected static final String SET_BLOB = "setBlob";
    protected static final String SET_BOOLEAN = "setBoolean";
    protected static final String SET_BYTE = "setByte";
    protected static final String SET_BYTES = "setBytes";
    protected static final String SET_CHARACTERSTREAM = "setCharacterStream";
    protected static final String SET_CLOB = "setClob";
    protected static final String SET_DATE = "setDate";
    protected static final String SET_DOUBLE = "setDouble";
    protected static final String SET_FLOAT = "setFloat";
    protected static final String SET_INT = "setInt";
    protected static final String SET_LONG = "setLong";
    protected static final String SET_NULL = "setNull";
    protected static final String SET_OBJECT = "setObject";
    protected static final String SET_REF = "setRef";
    protected static final String SET_SHORT = "setShort";
    protected static final String SET_STRING = "setString";
    protected static final String SET_TIME = "setTime";
    protected static final String SET_TIMESTAMP = "setTimestamp";
    protected static final String SET_UNICODESTREAM = "setUnicodeStream";
    protected static final String SET_URL = "setURL";
    protected static final String SET_ROWID = "setRowId";
    protected static final String SET_NSTRING = "setNString";
    protected static final String SET_NCHARACTERSTREAM = "setNCharacterStream";
    protected static final String SET_NCLOB = "setNClob";
    protected static final String SET_SQLXML = "setSQLXML";
    protected static final String CLEAR_PARAMETERS = "clearParameters";
    protected static final String[] PARAMETER_TYPES = {
            SET_ARRAY, SET_ASCIISTREAM, SET_BIGDECIMAL, SET_BINARYSTREAM, SET_BLOB, SET_BOOLEAN,
            SET_BYTE, SET_BYTES, SET_CHARACTERSTREAM, SET_CLOB, SET_DATE, SET_DOUBLE, SET_FLOAT,
            SET_INT, SET_LONG, SET_NULL, SET_OBJECT, SET_REF, SET_SHORT, SET_STRING, SET_TIME,
            SET_TIMESTAMP, SET_UNICODESTREAM, SET_URL, SET_ROWID, SET_NSTRING, SET_NCHARACTERSTREAM,
            SET_NCLOB, SET_SQLXML, CLEAR_PARAMETERS
    };

    protected static final Map<Character, String> JSON_SPECIAL_CHARS = new HashMap<Character, String>();

    static {
        JSON_SPECIAL_CHARS.put('"', "\\\"");   // quotation mark
        JSON_SPECIAL_CHARS.put('\\', "\\\\");  // reverse solidus
        JSON_SPECIAL_CHARS.put('/', "\\/");    // solidus
        JSON_SPECIAL_CHARS.put('\b', "\\b");   // backspace
        JSON_SPECIAL_CHARS.put('\f', "\\f");   // formfeed
        JSON_SPECIAL_CHARS.put('\n', "\\n");   // newline
        JSON_SPECIAL_CHARS.put('\r', "\\r");   // carriage return
        JSON_SPECIAL_CHARS.put('\t', "\\t");   // horizontal tab
    }

    public static enum StatementType {
        STATEMENT, PREPARED, CALLABLE
    }

    public static enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL, SYS_OUT;

        public static LogLevel of(String name) {
            return LogLevel.valueOf(name.toUpperCase());
        }
    }

    /**
     * Comparator considering string as integer.
     *
     * When it has null, put it as first element(smaller).
     * If string cannot be parsed to integer, it compared as string.
     */
    public static class StringAsIntegerComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            // make null first
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1; // right is greater
            }
            if (right == null) {
                return 1; // left is greater;
            }

            try {
                int leftValue = Integer.parseInt(left);
                int rightValue = Integer.parseInt(right);
                return Integer.compare(leftValue, rightValue);
            } catch (NumberFormatException e) {
                // use String comparison
                return left.compareTo(right);
            }
        }
    }

    // log output level
    protected LogLevel logLevel = LogLevel.INFO;

    // prefix string for output
    protected String prefix = "";

    // suffix string for output
    protected String suffix = "";

    // show query parameters
    protected boolean showParams = true;

    // report as json format
    protected boolean reportAsJson = false;


    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {

        Object proxyInstance = statement;  // fall back to statement if future unsupported method was called
        try {
            if (compare(CREATE_STATEMENT, method)) {
                proxyInstance =
                        Proxy.newProxyInstance(
                                QueryExecutionReportInterceptor.class.getClassLoader(),
                                new Class[]{Statement.class},
                                new StatementProxy((Statement) statement));
            } else if (compare(PREPARE_STATEMENT, method)) {
                //prepareStatement
                String sql = (String) args[0];
                proxyInstance =
                        Proxy.newProxyInstance(
                                QueryExecutionReportInterceptor.class.getClassLoader(),
                                new Class[]{PreparedStatement.class},
                                new PreparedAndCallableStatementProxy(
                                        StatementType.PREPARED, sql, (PreparedStatement) statement));
            } else if (compare(PREPARE_CALL, method)) {
                //prepareCall
                String sql = (String) args[0];
                proxyInstance =
                        Proxy.newProxyInstance(
                                QueryExecutionReportInterceptor.class.getClassLoader(),
                                new Class[]{CallableStatement.class},
                                new PreparedAndCallableStatementProxy(
                                        StatementType.CALLABLE, sql, (CallableStatement) statement));
            }
        } catch (Exception x) {
            log.warn("Unable to create statement proxy for full query report.", x);
        }

        return proxyInstance;
    }

    @Override
    public void closeInvoked() {
        // NOOP
    }

    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        InterceptorProperty p1 = properties.get("logLevel");
        InterceptorProperty p2 = properties.get("prefix");
        InterceptorProperty p3 = properties.get("suffix");
        InterceptorProperty p4 = properties.get("showParams");
        InterceptorProperty p5 = properties.get("reportAsJson");
        if (p1 != null) {
            setLogLevel(LogLevel.of(p1.getValue()));
        }
        if (p2 != null) {
            setPrefix(p2.getValue());
        }
        if (p3 != null) {
            setSuffix(p3.getValue());
        }
        if (p4 != null) {
            setShowParams(Boolean.parseBoolean(p4.getValue()));
        }
        if (p5 != null) {
            setReportAsJson(Boolean.parseBoolean(p5.getValue()));
        }
    }

    /**
     * Report(log) executed query information.
     *
     * @param queryExecutionInfo contains executed query information
     */
    protected void report(QueryExecutionInfo queryExecutionInfo) {

        if (!this.showParams) {
            // clear parameter information
            for (QueryInfo queryInfo : queryExecutionInfo.getQueries()) {
                queryInfo.getParams().clear();
            }
        }

        String logEntry;
        if (this.reportAsJson) {
            logEntry = getReportEntryAsJson(queryExecutionInfo);
        } else {
            logEntry = getReportEntry(queryExecutionInfo);
        }


        boolean hasPrefix = this.prefix != null && this.prefix.length() > 0;
        boolean hasSuffix = this.suffix != null && this.suffix.length() > 0;
        if (hasPrefix || hasSuffix) {
            StringBuilder sb = new StringBuilder();
            sb.append(this.prefix);
            sb.append(logEntry);
            sb.append(this.suffix);
            logEntry = sb.toString();
        }

        reportEntry(logEntry);

    }

    /**
     * Convert executed query information to single line string for reporting(logging).
     *
     * @param queryExecutionInfo contains executed query information
     * @return single line string to display
     */
    protected String getReportEntry(QueryExecutionInfo queryExecutionInfo) {

        StringBuilder sb = new StringBuilder();

        // query execution successful?
        sb.append("success:");
        sb.append(queryExecutionInfo.isSuccess());
        sb.append(", ");

        // statement type
        sb.append("type:");
        sb.append(queryExecutionInfo.getStatementType());
        sb.append(", ");

        // batch query?
        sb.append("batch:");
        sb.append(queryExecutionInfo.isBatch());
        sb.append(", ");

        // invocation time
        sb.append("time:");
        sb.append(queryExecutionInfo.getExecutionTime());
        sb.append(", ");

        // num of query
        sb.append("querySize:");
        sb.append(queryExecutionInfo.getQueries().size());
        sb.append(", ");

        // num of batch
        sb.append("batchSize:");
        sb.append(queryExecutionInfo.getBatchSize());
        sb.append(", ");


        // queries
        List<QueryInfo> queries = queryExecutionInfo.getQueries();
        sb.append("query:[");
        for (QueryInfo queryInfo : queries) {
            sb.append("(");
            sb.append(queryInfo.getQuery());
            sb.append("),");
        }
        chompIfEndWith(sb, ',');
        sb.append("], ");

        // query parameters
        sb.append("params:[");
        for (QueryInfo queryInfo : queries) {
            for (Map<String, String> paramMap : queryInfo.getParams()) {

                // sort
                SortedSet<String> paramKeys = new TreeSet<String>(new StringAsIntegerComparator());
                paramKeys.addAll(paramMap.keySet());

                sb.append("(");
                for (String paramKey : paramKeys) {
                    sb.append(paramKey);
                    sb.append("=");
                    sb.append(paramMap.get(paramKey));
                    sb.append(",");
                }
                chompIfEndWith(sb, ',');
                sb.append("),");
            }
        }
        chompIfEndWith(sb, ',');
        sb.append("]");

        return sb.toString();
    }

    protected void chompIfEndWith(StringBuilder sb, char c) {
        final int lastCharIndex = sb.length() - 1;
        if (sb.charAt(lastCharIndex) == c) {
            sb.deleteCharAt(lastCharIndex);
        }
    }

    /**
     * Convert executed query information to single line JSON string for reporting(logging).
     *
     * @param queryExecutionInfo contains executed query information
     * @return single line JSON string to display
     */
    protected String getReportEntryAsJson(QueryExecutionInfo queryExecutionInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append("{");

        // query execution successful?
        sb.append("\"success\":");
        sb.append(queryExecutionInfo.isSuccess());
        sb.append(", ");

        // statement type
        sb.append("\"type\":\"");
        sb.append(queryExecutionInfo.getStatementType());
        sb.append("\", ");

        // batch query?
        sb.append("\"batch\":");
        sb.append(queryExecutionInfo.isBatch());
        sb.append(", ");

        // invocation time
        sb.append("\"time\":");
        sb.append(queryExecutionInfo.getExecutionTime());
        sb.append(", ");

        // num of query
        sb.append("\"querySize\":");
        sb.append(queryExecutionInfo.getQueries().size());
        sb.append(", ");

        // num of batch
        sb.append("\"batchSize\":");
        sb.append(queryExecutionInfo.getBatchSize());
        sb.append(", ");


        // queries
        List<QueryInfo> queries = queryExecutionInfo.getQueries();
        sb.append("\"query\":[");
        for (QueryInfo queryInfo : queries) {
            sb.append("\"");
            sb.append(escapeSpecialCharacterForJson(queryInfo.getQuery()));
            sb.append("\",");
        }
        chompIfEndWith(sb, ',');
        sb.append("], ");

        // query parameters
        sb.append("\"params\":[");
        for (QueryInfo queryInfo : queries) {
            for (Map<String, String> paramMap : queryInfo.getParams()) {

                // sort params
                SortedSet<String> paramKey = new TreeSet<String>(new StringAsIntegerComparator());
                paramKey.addAll(paramMap.keySet());

                sb.append("{");
                for (String key : paramKey) {
                    sb.append("\"");
                    sb.append(escapeSpecialCharacterForJson(key));
                    sb.append("\":");
                    String paramValue = paramMap.get(key);
                    if (paramValue == null) {
                        sb.append("null");
                    } else {
                        sb.append("\"");
                        sb.append(escapeSpecialCharacterForJson(paramValue));
                        sb.append("\"");
                    }
                    sb.append(",");
                }
                chompIfEndWith(sb, ',');
                sb.append("},");
            }
        }
        chompIfEndWith(sb, ',');
        sb.append("]");


        sb.append("}");
        return sb.toString();
    }


    protected String escapeSpecialCharacterForJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String value = JSON_SPECIAL_CHARS.get(c);
            sb.append(value != null ? value : c);
        }
        return sb.toString();
    }

    /**
     * Report(log) given string.
     *
     * @param entry string to report(log)
     */
    protected void reportEntry(String entry) {
        if (this.logLevel == LogLevel.SYS_OUT) {
            System.out.println(entry);
        } else if (this.logLevel == LogLevel.TRACE) {
            log.trace(entry);
        } else if (this.logLevel == LogLevel.DEBUG) {
            log.debug(entry);
        } else if (this.logLevel == LogLevel.INFO) {
            log.info(entry);
        } else if (this.logLevel == LogLevel.WARN) {
            log.warn(entry);
        } else if (this.logLevel == LogLevel.ERROR) {
            log.error(entry);
        } else if (this.logLevel == LogLevel.FATAL) {
            log.fatal(entry);
        }
    }


    /**
     * Holds one query and multiple sets of parameters.
     *
     * For batch execution of PreparedStatement and CallableStatement, it will have one query with multiple
     * parameter sets.
     */
    public static class QueryInfo {
        private String query;
        private List<Map<String, String>> params = new ArrayList<Map<String, String>>();

        public QueryInfo(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }

        public List<Map<String, String>> getParams() {
            return params;
        }

    }

    /**
     * Data holder for reporting, representing single query execution(execute(), executeBatch(), etc).
     *
     * <p>This class holds all necessary query execution information for reporting.
     * When query(queries) are executed, instance of this class will be passed to {@link #report(QueryExecutionInfo)}.
     *
     * <p>For batch execution of Statement, it will have multiple QueryInfo in single batch execution.
     * <p>For batch execution of PreparedStatement or CallableStatement, it will have single QueryInfo which has multiple
     * set of parameters.
     */
    public static class QueryExecutionInfo {
        private List<QueryInfo> queries = new ArrayList<QueryInfo>();
        private boolean batch = false;
        private boolean success = false;
        private int batchSize;
        private long executionTime;
        private StatementType statementType;

        public boolean isBatch() {
            return batch;
        }

        public void setBatch(boolean batch) {
            this.batch = batch;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public List<QueryInfo> getQueries() {
            return queries;
        }

        public void setQueries(List<QueryInfo> queries) {
            this.queries = queries;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }

        public void setStatementType(StatementType statementType) {
            this.statementType = statementType;
        }

        public StatementType getStatementType() {
            return statementType;
        }

        public boolean isStatement() {
            return StatementType.STATEMENT.equals(this.statementType);
        }

        public boolean isPreparedStatement() {
            return StatementType.PREPARED.equals(this.statementType);
        }

        public boolean isCallableStatement() {
            return StatementType.CALLABLE.equals(this.statementType);
        }
    }

    /**
     * Proxy to keep truck of execution info for {@link java.sql.Statement}.
     */
    protected class StatementProxy implements InvocationHandler {
        private List<String> queries = new ArrayList<String>();
        private Statement delegate;

        public StatementProxy(Statement delegate) {
            this.delegate = delegate;
        }

        public void clear() {
            this.queries.clear();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            boolean isExecuteMethod = isExecute(method, false);
            boolean isExecuteNonBatchMethod = isExecuteMethod && !compare(EXECUTE_BATCH, method);
            boolean isBatchMethod = process(BATCH_PARAM_TYPES, method, false);

            if (isBatchMethod) {
                // for batch related operation (except executeBatch())
                if (compare(ADD_BATCH, method)) {
                    String query = (String) args[0];
                    this.queries.add(query);
                } else if (compare(CLEAR_BATCH, method)) {
                    this.queries.clear();
                }
            } else if (isExecuteNonBatchMethod) {
                // execute method but not "executeBatch()"
                String query = (String) args[0];
                this.queries.add(query);
            }

            Object result;
            boolean success = false;
            long start = System.currentTimeMillis();
            try {
                result = method.invoke(this.delegate, args);  //execute the query
                success = true;
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            } finally {
                if (isExecuteMethod) {
                    boolean batch = compare(EXECUTE_BATCH, method);
                    long delta = System.currentTimeMillis() - start;
                    QueryExecutionInfo executionInfo = createQueryExecutionInfo(success, batch, delta);
                    report(executionInfo);
                    clear();
                }
            }

            return result;
        }

        private QueryExecutionInfo createQueryExecutionInfo(boolean success, boolean batch,
                                                            long totalInvocationTime) {
            QueryExecutionInfo queryExecutionInfo = new QueryExecutionInfo();
            queryExecutionInfo.setSuccess(success);
            queryExecutionInfo.setBatch(batch);
            queryExecutionInfo.setStatementType(StatementType.STATEMENT);
            queryExecutionInfo.setExecutionTime(totalInvocationTime);
            queryExecutionInfo.setBatchSize(batch ? this.queries.size() : 0);
            for (String query : this.queries) {
                queryExecutionInfo.getQueries().add(new QueryInfo(query));
            }
            return queryExecutionInfo;
        }
    }

    /**
     * Callback operation to format the given query parameters for reporting.
     * It is called when setXxx operations are invoked for PreparedStatement and CallableStatement.
     *
     * @param query  current query
     * @param method invoked method ("setXxx")
     * @param args   arguments for invoked method
     * @return String value for reporting
     */
    protected String convertParameter(String query, StatementType statementType, Method method, Object[] args) {
        if (compare(SET_NULL, method)) {
            return null;
        }
        Object value = args[1];  // for setXxx operation, second arg is parameter value
        if (value != null) {
            return value.toString();  // default strategy is to use toString().
        }
        return null;
    }

    /**
     * Proxy to keep truck of execution info for {@link java.sql.PreparedStatement} and {@link java.sql.CallableStatement}.
     */
    protected class PreparedAndCallableStatementProxy implements InvocationHandler {
        private PreparedStatement delegate;
        private String query;
        private StatementType statementType;

        // keep track of current parameters
        private Map<String, String> parameters = new HashMap<String, String>();
        private List<Map<String, String>> batchParameters = new ArrayList<Map<String, String>>();

        public PreparedAndCallableStatementProxy(StatementType statementType, String query, PreparedStatement delegate) {
            this.statementType = statementType;
            this.query = query;
            this.delegate = delegate;
        }

        private void clear() {
            this.query = "";
            this.parameters.clear();
            this.batchParameters.clear();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            boolean isExecuteMethod = isExecute(method, false);
            boolean isParameterMethod = !isExecuteMethod && process(PARAMETER_TYPES, method, false);
            boolean isBatchMethod = !isParameterMethod && process(BATCH_PARAM_TYPES, method, false);

            if (isParameterMethod) {
                if (compare(CLEAR_PARAMETERS, method)) {
                    parameters.clear();
                } else {
                    String parameterKey = null;
                    String parameter = convertParameter(this.query, this.statementType, method, args);

                    // CallableStatement may use named parameter
                    if (args[0] != null) {
                        if (StatementType.CALLABLE.equals(this.statementType) && args[0] instanceof String) {
                            parameterKey = (String) args[0];  // use named parameter as is
                        } else {
                            Integer index = (Integer) args[0];
                            parameterKey = index.toString(); // use parameterIndex as a String
                        }
                    }
                    parameters.put(parameterKey, parameter);
                }
            } else if (isBatchMethod) {
                if (compare(ADD_BATCH, method)) {
                    // copy parameter values
                    batchParameters.add(new LinkedHashMap<String, String>(this.parameters));
                    parameters.clear();
                } else if (compare(CLEAR_BATCH, method)) {
                    batchParameters.clear();
                }
            }

            Object result;
            boolean success = false;
            long start = System.currentTimeMillis();
            try {
                //execute the query
                result = method.invoke(this.delegate, args);
                success = true;
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            } finally {
                if (isExecuteMethod) {
                    boolean batch = compare(EXECUTE_BATCH, method);
                    long delta = System.currentTimeMillis() - start;
                    QueryExecutionInfo executionInfo = createQueryExecutionInfo(success, batch, delta);
                    report(executionInfo);

                    this.clear();
                }
            }
            return result;
        }

        private QueryExecutionInfo createQueryExecutionInfo(boolean success, boolean batch,
                                                            long totalInvocationTime) {
            // for PreparedStatement and CallableStatement, there will be one query string and
            // one parameter map or multiple parameter maps if batch query.
            QueryExecutionInfo queryExecutionInfo = new QueryExecutionInfo();
            queryExecutionInfo.setSuccess(success);
            queryExecutionInfo.setBatch(batch);
            queryExecutionInfo.setStatementType(this.statementType);
            queryExecutionInfo.setExecutionTime(totalInvocationTime);

            QueryInfo queryInfo = new QueryInfo(this.query);
            if (batch) {
                // copy parameters for batch query
                for (Map<String, String> batchParameter : this.batchParameters) {
                    queryInfo.getParams().add(new HashMap<String, String>(batchParameter));
                }
                queryExecutionInfo.setBatchSize(this.batchParameters.size());
            } else {
                // copy parameters for non batch query
                queryInfo.getParams().add(new HashMap<String, String>(this.parameters));
            }
            queryExecutionInfo.getQueries().add(queryInfo);

            return queryExecutionInfo;
        }

    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public void setShowParams(boolean showParams) {
        this.showParams = showParams;
    }

    public void setReportAsJson(boolean reportAsJson) {
        this.reportAsJson = reportAsJson;
    }
}
