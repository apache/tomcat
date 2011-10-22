/*
 */
package org.apache.tomcat.lite.load;

import org.apache.tomcat.lite.http.BaseMapper;
import org.apache.tomcat.lite.http.MappingData;
import org.apache.tomcat.lite.io.CBuffer;

import junit.framework.TestCase;

public class MicroTest extends TestCase {

    public void testMapper() throws Exception {
        BaseMapper mapper = new BaseMapper();

        MappingData mappingData = new MappingData();
        CBuffer host = CBuffer.newInstance();
        host.set("test1.com");

        CBuffer uri = CBuffer.newInstance();
        uri.set("/foo/bar/blah/bobou/foo");

        String[] welcomes = new String[2];
        welcomes[0] = "index.html";
        welcomes[1] = "foo.html";

        for (int i = 0; i < 100; i++) {
            String hostN = "test" + i + ".com";
            mapper.addContext(hostN, "", "context0", new String[0], null, null);
            mapper.addContext(hostN, "/foo", "context1", new String[0], null, null);
            mapper.addContext(hostN, "/foo/bar", "context2", welcomes, null, null);
            mapper.addContext(hostN, "/foo/bar/bla", "context3", new String[0], null, null);

            mapper.addWrapper(hostN, "/foo/bar", "/fo/*", "wrapper0");
        }
        int N = 10000;
        for (int i = 0; i < N; i++) {
            mappingData.recycle();
            mapper.map(host, uri, mappingData);
        }

        long time = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            mappingData.recycle();
            mapper.map(host, uri, mappingData);
        }
        // TODO: asserts
        //System.out.println("Elapsed:" + (System.currentTimeMillis() - time));
    }
}
