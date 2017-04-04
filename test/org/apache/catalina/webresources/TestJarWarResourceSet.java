package org.apache.catalina.webresources;

import java.io.File;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestJarWarResourceSet extends TomcatBaseTest {

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }

    
	@Test
	public void testJarWarMetaInf() throws LifecycleException  {
        Tomcat tomcat = getTomcatInstance();

		File warFile = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addContext("", warFile.getAbsolutePath());
        
        tomcat.start();

        StandardRoot root = (StandardRoot) ctx.getResources();
        
		WebResource[] results = root.getClassLoaderResources("/META-INF");
		
		Assert.assertNotNull(results);
		Assert.assertEquals(1, results.length);
		Assert.assertNotNull(results[0].getURL());
	}
}
