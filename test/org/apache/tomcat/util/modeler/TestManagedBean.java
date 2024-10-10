package org.apache.tomcat.util.modeler;

import java.util.stream.Stream;

import javax.management.MBeanInfo;

import org.apache.coyote.Response;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagedBean {
    private ManagedBean managedBean = null;

    @Before
    public void setUp() throws Exception {
        managedBean = new ManagedBean();

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetMBeanInfo() {

        MBeanInfo mbeanInfo = managedBean.getMBeanInfo();
        Assert.assertTrue(Stream.of(mbeanInfo.getAttributes()).filter(m -> m.getType().equals(Response.class.getName()))
                .count() == 0);
        Assert.assertTrue(Stream.of(mbeanInfo.getOperations()).count() == 0);
        Assert.assertTrue(Stream.of(mbeanInfo.getNotifications()).count() == 0);

        AttributeInfo attr = new AttributeInfo();
        attr.setGetMethod("getResponse");
        attr.setName("response");
        attr.setReadable(true);
        attr.setWriteable(false);
        attr.setType(Response.class.getName());
        managedBean.addAttribute(attr);
        mbeanInfo = managedBean.getMBeanInfo();
        Assert.assertEquals(1,
                Stream.of(mbeanInfo.getAttributes()).filter(m -> m.getType().equals(Response.class.getName())).count());

        OperationInfo op = new OperationInfo();
        op.setImpact("info");
        op.setType("getter");
        op.setReturnType(Response.class.getName());
        op.setName("getResponse");
        managedBean.addOperation(op);

        op = new OperationInfo();
        op.setImpact("action");
        op.setType("setter");
        op.setReturnType(Void.TYPE.getName());
        op.setName("setResponse");
        managedBean.addOperation(op);
        mbeanInfo = managedBean.getMBeanInfo();
        Assert.assertEquals("attr: response expected.", 1,
                Stream.of(mbeanInfo.getAttributes()).filter(m -> m.getType().equals(Response.class.getName())).count());
        Assert.assertEquals("ops: getResponse expected.", 1,
                Stream.of(mbeanInfo.getOperations()).filter(
                        m -> Response.class.getName().equals(m.getReturnType()) && "getResponse".equals(m.getName()))
                        .count());
        Assert.assertEquals("ops: setResponse expected.", 1,
                Stream.of(mbeanInfo.getOperations())
                        .filter(m -> Void.TYPE.getName().equals(m.getReturnType()) && "setResponse".equals(m.getName()))
                        .count());

        NotificationInfo notify = new NotificationInfo();
        notify.setName("NotifyOfTomcat");
        managedBean.addNotification(notify);
        mbeanInfo = managedBean.getMBeanInfo();
        Assert.assertEquals("attr: response expected.", 1,
                Stream.of(mbeanInfo.getAttributes()).filter(m -> m.getType().equals(Response.class.getName())).count());
        Assert.assertEquals("ops: getResponse expected.", 1,
                Stream.of(mbeanInfo.getOperations()).filter(
                        m -> Response.class.getName().equals(m.getReturnType()) && "getResponse".equals(m.getName()))
                        .count());
        Assert.assertEquals("ops: setResponse expected.", 1,
                Stream.of(mbeanInfo.getOperations())
                        .filter(m -> Void.TYPE.getName().equals(m.getReturnType()) && "setResponse".equals(m.getName()))
                        .count());
        Assert.assertEquals("notify: NotifyOfTomcat expected", 1,
                Stream.of(mbeanInfo.getNotifications()).filter(m -> "NotifyOfTomcat".equals(m.getName())).count());
    }

}
