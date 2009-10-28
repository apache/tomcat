package org.apache.catalina.startup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Test case for {@link ContextConfig}.
 */
public class TestContextConfig extends TestCase {
    private WebXml app;
    private WebXml f1;
    private WebXml f2;
    private WebXml f3;
    private WebXml f4;
    private WebXml f5;
    private Map<String,WebXml> fragments;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        app = new WebXml();
        f1 = new WebXml();
        f1.setName("f1");
        f2 = new WebXml();
        f2.setName("f2");
        f3 = new WebXml();
        f3.setName("f3");
        f4 = new WebXml();
        f4.setName("f4");
        f5 = new WebXml();
        f5.setName("f5");
        fragments = new HashMap<String,WebXml>();
        fragments.put("f1",f1);
        fragments.put("f2",f2);
        fragments.put("f3",f3);
        fragments.put("f4",f4);
        fragments.put("f5",f5);
    }


    public void testOrderWebFragmentsAbsolute() {
        app.addAbsoluteOrdering("f3");
        app.addAbsoluteOrdering("f1");
        app.addAbsoluteOrdering("f2");
        app.addAbsoluteOrdering("f5");
        app.addAbsoluteOrdering("f4");
        
        Set<WebXml> ordered = ContextConfig.orderWebFragments(app, fragments);
        
        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(f3,iter.next());
        assertEquals(f1,iter.next());
        assertEquals(f2,iter.next());
        assertEquals(f5,iter.next());
        assertEquals(f4,iter.next());
        assertFalse(iter.hasNext());
    }

    public void testOrderWebFragmentsAbsolutePartial() {
        app.addAbsoluteOrdering("f3");
        app.addAbsoluteOrdering("f1");
        
        Set<WebXml> ordered = ContextConfig.orderWebFragments(app, fragments);
        
        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(f3,iter.next());
        assertEquals(f1,iter.next());
        assertFalse(iter.hasNext());
    }

    public void testOrderWebFragmentsAbsoluteOthersStart() {
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("f2");
        app.addAbsoluteOrdering("f4");
        
        Set<WebXml> others = new HashSet<WebXml>();
        others.add(f1);
        others.add(f3);
        others.add(f5);
        
        Set<WebXml> ordered = ContextConfig.orderWebFragments(app, fragments);
        
        Iterator<WebXml> iter = ordered.iterator();
        while (others.size() > 0) {
            WebXml f = iter.next();
            assertTrue(others.contains(f));
            others.remove(f);
        }
        assertEquals(f2,iter.next());
        assertEquals(f4,iter.next());
        assertFalse(iter.hasNext());
    }

    public void testOrderWebFragmentsAbsoluteOthersMiddle() {
        app.addAbsoluteOrdering("f2");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("f4");
        
        Set<WebXml> others = new HashSet<WebXml>();
        others.add(f1);
        others.add(f3);
        others.add(f5);
        
        Set<WebXml> ordered = ContextConfig.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(f2,iter.next());

        while (others.size() > 0) {
            WebXml f = iter.next();
            assertTrue(others.contains(f));
            others.remove(f);
        }
        assertEquals(f4,iter.next());
        assertFalse(iter.hasNext());
    }

    public void testOrderWebFragmentsAbsoluteOthersEnd() {
        app.addAbsoluteOrdering("f2");
        app.addAbsoluteOrdering("f4");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        
        Set<WebXml> others = new HashSet<WebXml>();
        others.add(f1);
        others.add(f3);
        others.add(f5);
        
        Set<WebXml> ordered = ContextConfig.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(f2,iter.next());
        assertEquals(f4,iter.next());

        while (others.size() > 0) {
            WebXml f = iter.next();
            assertTrue(others.contains(f));
            others.remove(f);
        }
        assertFalse(iter.hasNext());
    }

}
