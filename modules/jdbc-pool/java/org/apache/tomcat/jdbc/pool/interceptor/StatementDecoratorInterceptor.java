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
 
 
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractCreateStatementInterceptor;

/**
 * Implementation of <b>JdbcInterceptor</b> that proxies resultSets and statements.
 * @author Guillermo Fernandes
 */
public class StatementDecoratorInterceptor extends AbstractCreateStatementInterceptor {

    private static final Log logger = LogFactory.getLog(StatementDecoratorInterceptor.class);

    private static final String[] EXECUTE_QUERY_TYPES = { "executeQuery" };

    /**
     * the constructors that are used to create statement proxies
     */
    protected static final Constructor<?>[] constructors = new Constructor[AbstractCreateStatementInterceptor.STATEMENT_TYPE_COUNT];

    /**
     * the constructor to create the resultSet proxies
     */
    protected static Constructor<?> resultSetConstructor = null;

    @Override
    public void closeInvoked() {
        // nothing to do
    }

    /**
     * Creates a constructor for a proxy class, if one doesn't already exist
     * 
     * @param idx
     *            - the index of the constructor
     * @param clazz
     *            - the interface that the proxy will implement
     * @return - returns a constructor used to create new instances
     * @throws NoSuchMethodException
     */
    protected Constructor<?> getConstructor(int idx, Class<?> clazz) throws NoSuchMethodException {
        if (constructors[idx] == null) {
            Class<?> proxyClass = Proxy.getProxyClass(StatementDecoratorInterceptor.class.getClassLoader(),
                    new Class[] { clazz });
            constructors[idx] = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
        }
        return constructors[idx];
    }

    protected Constructor<?> getResultSetConstructor() throws NoSuchMethodException {
        if (resultSetConstructor == null) {
            Class<?> proxyClass = Proxy.getProxyClass(StatementDecoratorInterceptor.class.getClassLoader(),
                    new Class[] { ResultSet.class });
            resultSetConstructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
        }
        return resultSetConstructor;
    }

    /**
     * Creates a statement interceptor to monitor query response times
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            Object result = null;
            String name = method.getName();
            Constructor<?> constructor = null;
            if (compare(CREATE_STATEMENT, name)) {
                // createStatement
                constructor = getConstructor(CREATE_STATEMENT_IDX, Statement.class);
            } else if (compare(PREPARE_STATEMENT, name)) {
                // prepareStatement
                constructor = getConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
            } else if (compare(PREPARE_CALL, name)) {
                // prepareCall
                constructor = getConstructor(PREPARE_IDX, CallableStatement.class);
            } else {
                // do nothing, might be a future unsupported method
                // so we better bail out and let the system continue
                return statement;
            }
            StatementProxy statementProxy = new StatementProxy(statement);
            result = constructor.newInstance(new Object[] { statementProxy });
            statementProxy.setActualProxy(result);
            statementProxy.setConnection(proxy);
            return result;
        } catch (Exception x) {
            logger.warn("Unable to create statement proxy for slow query report.", x);
        }
        return statement;
    }

    protected boolean isExecuteQuery(String methodName) {
        return EXECUTE_QUERY_TYPES[0].equals(methodName);
    }

    protected boolean isExecuteQuery(Method method) {
        return isExecuteQuery(method.getName());
    }

    /**
     * Class to measure query execute time
     * 
     * @author fhanik
     * 
     */
    protected class StatementProxy implements InvocationHandler {
        
        protected boolean closed = false;
        protected Object delegate;
        private Object actualProxy;
        private Object connection;

        public StatementProxy(Object parent) {
            this.delegate = parent;
        }

        public void setConnection(Object proxy) {
            this.connection = proxy;            
        }

        public void setActualProxy(Object proxy){
            this.actualProxy = proxy;
        }
        
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // get the name of the method for comparison
            final String name = method.getName();
            // was close invoked?
            boolean close = compare(JdbcInterceptor.CLOSE_VAL, name);
            // allow close to be called multiple times
            if (close && closed)
                return null;
            // are we calling isClosed?
            if (compare(JdbcInterceptor.ISCLOSED_VAL, name))
                return Boolean.valueOf(closed);
            // if we are calling anything else, bail out
            if (closed)
                throw new SQLException("Statement closed.");
            if (name.equals("getConnection")){
                return connection;
            }
            boolean process = isExecuteQuery(method);
            // check to see if we are about to execute a query
            // if we are executing, get the current time
            Object result = null;
            try {
                // execute the query
                result = method.invoke(delegate, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    InvocationTargetException it = (InvocationTargetException) t;
                    throw it.getCause() != null ? it.getCause() : it;
                } else {
                    throw t;
                }
            }
            // perform close cleanup
            if (close) {
                closed = true;
                delegate = null;
            }
            if (process){
                Constructor<?> cons = getResultSetConstructor();
                result = cons.newInstance(new Object[]{new ResultSetProxy(actualProxy, result)});
            }
            return result;
        }
    }

    protected class ResultSetProxy implements InvocationHandler {

        private Object st;
        private Object delegate;

        public ResultSetProxy(Object st, Object delegate) {
            this.st = st;
            this.delegate = delegate;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getStatement")) {
                return this.st;
            } else {
                return method.invoke(this.delegate, args);
            }
        }
    }
}
