package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.sql.Statement;

import org.apache.tomcat.jdbc.pool.DataSourceProxy;
import org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;

public class StatementFinalizerTest extends DefaultTestCase {

    public StatementFinalizerTest(String name) {
        super(name);
    }
    
    public void testStatementFinalization() throws Exception {
        this.init();
        datasource.setJdbcInterceptors(StatementFinalizer.class.getName());
        Connection con = datasource.getConnection();
        Statement st = con.createStatement();
        assertFalse("Statement should not be closed.",st.isClosed());
        con.close();
        assertTrue("Statement should be closed.",st.isClosed());
    }

}
