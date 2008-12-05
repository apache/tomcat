package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Random;



public class CreateTestTable extends DefaultTestCase {

    public CreateTestTable(String name) {
        super(name);
    }
    
    public void testPopulateData() throws Exception {
        String insert = "insert into test values (?,?,?,?,?)";
        this.init();
        Connection con = datasource.getConnection();
        Statement st = con.createStatement();
        try {
            st.execute("drop table test");
        }catch (Exception ignore) {}
        st.execute("create table test(id int not null, val1 varchar(255), val2 varchar(255), val3 varchar(255), val4 varchar(255))");
        st.close();
        PreparedStatement ps = con.prepareStatement(insert);
        for (int i=0; i<10000000; i++) {
            ps.setInt(1,i);
            String s = getRandom();
            ps.setString(2, s);
            ps.setString(3, s);
            ps.setString(4, s);
            ps.setString(5, s);
            ps.addBatch();
            ps.clearParameters();
            if ((i+1) % 1000 == 0) {
                System.out.print(".");
            }
            if ((i+1) % 10000 == 0) {
                System.out.print("\n"+(i+1));
                ps.executeBatch();
                ps.clearBatch();
            }

        }
        ps.close();
        con.close();
    }
    
    public static Random random = new Random(System.currentTimeMillis());
    public static String getRandom() {
        StringBuffer s = new StringBuffer(256);
        for (int i=0;i<254; i++) {
            int b = Math.abs(random.nextInt() % 29);
            char c = (char)(b+65);
            s.append(c);
        }
        return s.toString();
    }

}
