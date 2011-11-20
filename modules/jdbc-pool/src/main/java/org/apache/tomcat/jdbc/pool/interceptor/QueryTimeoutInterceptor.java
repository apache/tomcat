package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

public class QueryTimeoutInterceptor extends AbstractCreateStatementInterceptor {
    private static Log log = LogFactory.getLog(QueryTimeoutInterceptor.class);
    int timeout;
    
    @Override
    public void setProperties(Map<String,InterceptorProperty> properties) {
        super.setProperties(properties);
        timeout = properties.get("queryTimeout").getValueAsInt(-1);
    }

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        if (statement instanceof Statement && timeout > 0) {
            Statement s = (Statement)statement;
            try {
                s.setQueryTimeout(timeout);
            }catch (SQLException x) {
                log.warn("[QueryTimeoutInterceptor] Unable to set query timeout:"+x.getMessage(),x);
            }
        }
        return statement;
    }

    @Override
    public void closeInvoked() {
    }

}
