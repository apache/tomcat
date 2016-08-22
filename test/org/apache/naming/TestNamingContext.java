package org.apache.naming;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.naming.factory.ResourceLinkFactory;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.junit.Assert;
import org.junit.Test;

public class TestNamingContext extends TomcatBaseTest {

    private static final String COMP_ENV = "comp/env";
    private static final String GLOBAL_NAME = "global";
    private static final String LOCAL_NAME = "local";
    private static final String DATA = "Cabbage";


    @Test
    public void testGlobalNaming() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.enableNaming();

        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        tomcat.start();

        Context webappInitial = ContextBindings.getContext(ctx);

        // Nothing added at the moment so should be null
        Object obj = doLookup(webappInitial, COMP_ENV + "/" + LOCAL_NAME);
        Assert.assertNull(obj);

        ContextEnvironment ce = new ContextEnvironment();
        ce.setName(GLOBAL_NAME);
        ce.setValue(DATA);
        ce.setType(DATA.getClass().getName());

        tomcat.getServer().getGlobalNamingResources().addEnvironment(ce);

        // No link so still should be null
        obj = doLookup(webappInitial, COMP_ENV + "/" + LOCAL_NAME);
        Assert.assertNull(obj);

        // Now add a resource link to the context
        ContextResourceLink crl = new ContextResourceLink();
        crl.setGlobal(GLOBAL_NAME);
        crl.setName(LOCAL_NAME);
        crl.setType(DATA.getClass().getName());
        ctx.getNamingResources().addResourceLink(crl);

        // Link exists so should be OK now
        obj = doLookup(webappInitial, COMP_ENV + "/" + LOCAL_NAME);
        Assert.assertEquals(DATA, obj);

        // Try shortcut
        ResourceLinkFactory factory = new ResourceLinkFactory();
        ResourceLinkRef rlr = new ResourceLinkRef(DATA.getClass().getName(), GLOBAL_NAME, null, null);
        obj = factory.getObjectInstance(rlr, null, null, null);
        Assert.assertEquals(DATA, obj);

        // Remove the link
        ctx.getNamingResources().removeResourceLink(LOCAL_NAME);

        // No link so should be null
        obj = doLookup(webappInitial, COMP_ENV + "/" + LOCAL_NAME);
        Assert.assertNull(obj);

        // Shortcut should fail too
        obj = factory.getObjectInstance(rlr, null, null, null);
        Assert.assertNull(obj);
    }


    private Object doLookup(Context context, String name) {
        Object result = null;
        try {
            result = context.lookup(name);
        } catch (NamingException nnfe) {
            // Ignore
        }
        return result;
    }
}
