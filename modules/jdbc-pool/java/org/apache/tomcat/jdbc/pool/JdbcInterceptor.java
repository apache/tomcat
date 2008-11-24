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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public abstract class JdbcInterceptor implements InvocationHandler {
    public  static final String CLOSE_VAL = "close";
    public  static final String TOSTRING_VAL = "toString";
    public  static final String ISCLOSED_VAL = "isClosed"; 
    
    protected List<InterceptorProperty> properties = null; 
    
    private JdbcInterceptor next = null;
    private boolean useEquals = false;

    public JdbcInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (getNext()!=null) return getNext().invoke(this,method,args);
        else throw new NullPointerException();
    }

    public JdbcInterceptor getNext() {
        return next;
    }

    public void setNext(JdbcInterceptor next) {
        this.next = next;
    }
    
    public boolean compare(String name1, String name2) {
        if (useEquals()) {
            return name1.equals(name2);
        } else {
            return name1==name2;
        }
    }
    
    public boolean compare(String methodName, Method method) {
        return compare(methodName, method.getName());
    }
    
    /**
     * Gets called each time the connection is borrowed from the pool
     * @param parent - the connection pool owning the connection
     * @param con - the pooled connection
     */
    public abstract void reset(ConnectionPool parent, PooledConnection con);

    public List<InterceptorProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<InterceptorProperty> properties) {
        this.properties = properties;
    }
    
    public boolean getUseEquals() {
        return useEquals();
    }
    
    public boolean useEquals() {
        return getUseEquals();
    }
    
    public void setUseEquals(boolean useEquals) {
        this.useEquals = useEquals;
    }
}
