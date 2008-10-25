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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.SQLException;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class SlowQueryReport extends JdbcInterceptor {
    protected final String[] statements = {"createStatement","prepareStatement","prepareCall"};
    protected final String[] executes = {"execute","executeQuery","executeUpdate","executeBatch"};

    public SlowQueryReport() {
        super();
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean process = false;
        process = process(statements, method, process);
        if (process) {
            Object statement = super.invoke(proxy,method,args);
            CallableStatement measuredStatement =
                (CallableStatement)Proxy.newProxyInstance(SlowQueryReport.class.getClassLoader(),
                    new Class[] {java.sql.CallableStatement.class,
                                 java.sql.PreparedStatement.class,
                                 java.sql.Statement.class},
                    new StatementProxy(statement, args));

            return measuredStatement;
        } else {
            return super.invoke(proxy,method,args);
        }
    }

    protected boolean process(String[] names, Method method, boolean process) {
        for (int i=0; (!process) && i<names.length; i++) {
            process = (method.getName()==names[i]);
        }
        return process;
    }

    protected class StatementProxy implements InvocationHandler {
        protected Object parent;
        protected Object[] args;
        public StatementProxy(Object parent, Object[] args) {
            this.parent = parent;
            this.args = args;
        }
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (this.parent == null ) throw new SQLException("Statement has been closed.");
            boolean process = false;
            process = process(executes, method, process);
            long start = (process)?System.currentTimeMillis():0;
            //execute the query
            Object result =  method.invoke(parent,args);
            long delta = (process)?(System.currentTimeMillis()-start):0;
            if (delta>10) {
                StringBuffer out = new StringBuffer("\n\tType:");
                out.append(parent.getClass().getName());
                out.append("\n\tCreate/Prepare args:");
                for (int i=0; this.args!=null && i<this.args.length;i++) {
                    out.append(this.args[i]!=null?this.args[i]:"null");
                    out.append("; ");
                }
                out.append("\n\tExecute args:");
                for (int i=0; args!=null && i<args.length;i++) {
                    out.append(args[i]!=null?args[i]:"null");
                    out.append("; ");
                }
                System.out.println("Slow query:"+out+"\nTime to execute:"+(delta)+" ms.");
            }
            if (JdbcInterceptor.CLOSE_VAL==method.getName()) {
                this.parent = null;
                this.args = null;
            }
            return result;
        }
    }

    public void reset(ConnectionPool parent, PooledConnection con) {

    }
}
