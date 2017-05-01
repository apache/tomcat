package org.apache.tomcat.jdbc.bugs;

import org.apache.tomcat.jdbc.test.DefaultTestCase;
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

    @Test
    public void testSizeAfterMutlipleAbandonedConnections() throws Exception {
        assertThat(this.datasource.getPool().getSize(), is(CONNECTION_POOL_SIZE));
        runLongRunningConnections(CONNECTION_POOL_SIZE);
        assertThat(this.datasource.getPool().getSize(), is(CONNECTION_POOL_SIZE));
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
