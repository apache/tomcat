package org.apache.tomcat.jdbc.bugs;

import org.apache.tomcat.jdbc.test.DefaultTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class Bug61032 extends DefaultTestCase {
    private static final int CONNECTION_POOL_SIZE = 3;

    @Before
    public void setup() {
        this.datasource.setMaxActive(CONNECTION_POOL_SIZE);
        this.datasource.setMaxIdle(CONNECTION_POOL_SIZE);
        this.datasource.setInitialSize(CONNECTION_POOL_SIZE);
        this.datasource.setMinIdle(CONNECTION_POOL_SIZE);
        this.datasource.getPoolProperties().setAbandonWhenPercentageFull(0);
        this.datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(100);
        this.datasource.getPoolProperties().setRemoveAbandoned(true);
        this.datasource.getPoolProperties().setRemoveAbandonedTimeout(1);

        this.datasource.setLogAbandoned(true);
        this.datasource.setLogValidationErrors(true);
    }

    @After
    @Override
    public void tearDown() {
        System.out.println("START TEAR DOWN!");
        try {
            datasource.close();
        } catch (Exception ignore){
            // Ignore
        }
        try {
            tDatasource.close();
        } catch (Exception ignore){
            // Ignore
        }
        //try {((ComboPooledDataSource)c3p0Datasource).close(true);}catch(Exception ignore){}
        datasource = null;
        tDatasource = null;
        System.gc();
        org.apache.tomcat.jdbc.test.driver.Driver.reset();
        System.out.println("FINISHED TEAR DOWN!");
    }

    @Test
    public void testSizeAfterMutlipleAbandonedConnections() throws Exception {
        assertThat(this.datasource.getPool().getSize(), is(CONNECTION_POOL_SIZE));
        runLongRunningConnections(CONNECTION_POOL_SIZE);
        assertThat(this.datasource.getPool().getSize(), is(CONNECTION_POOL_SIZE));
        System.out.println("FINISHED!!");
    }

    private void runLongRunningConnections(int numberOfLongRunningConnections) {
        for (int i = 1; i <= numberOfLongRunningConnections; i++) {
            try (Connection connection = this.datasource.getConnection()) {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException e) {
                    // NO OP
                }
                connection.close();
            } catch (SQLException ex) {
                // NO OP
            }
        }
    }
}
