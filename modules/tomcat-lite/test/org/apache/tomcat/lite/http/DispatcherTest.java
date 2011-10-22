/*
 */
package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.CBuffer;

import junit.framework.TestCase;

public class DispatcherTest extends TestCase {

    public void testMapper() throws Exception {
        BaseMapper mapper = new BaseMapper();

        String[] welcomes = new String[2];
        welcomes[0] = "index.html";
        welcomes[1] = "foo.html";

        mapper.addContext("test1.com", "", "context0", new String[0], null, null);
        mapper.addContext("test1.com", "/foo", "context1", new String[0], null, null);
        mapper.addContext("test1.com", "/foo/bar", "context2", welcomes, null, null);
        mapper.addContext("test1.com", "/foo/bar/bla", "context3", new String[0], null, null);

        mapper.addWrapper("test1.com", "/foo/bar", "/fo/*", "wrapper0");
        mapper.addWrapper("test1.com", "/foo/bar", "/", "wrapper1");
        mapper.addWrapper("test1.com", "/foo/bar", "/blh", "wrapper2");
        mapper.addWrapper("test1.com", "/foo/bar", "*.jsp", "wrapper3");
        mapper.addWrapper("test1.com", "/foo/bar", "/blah/bou/*", "wrapper4");
        mapper.addWrapper("test1.com", "/foo/bar", "/blah/bobou/*", "wrapper5");
        mapper.addWrapper("test1.com", "/foo/bar", "*.htm", "wrapper6");

        mapper.addContext("asdf.com", "", "context0", new String[0], null, null);

        MappingData mappingData = new MappingData();

        CBuffer host = CBuffer.newInstance();
        host.set("test1.com");

        CBuffer uri = CBuffer.newInstance();
        uri.set("/foo/bar/blah/bobou/foo");

        mapper.map(host, uri, mappingData);

        assertEquals("context2", mappingData.context.toString());
        assertEquals("/foo/bar", mappingData.contextPath.toString());
    }
}
