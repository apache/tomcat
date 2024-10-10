package org.apache.tomcat.util.modeler;

import javax.management.MBeanOperationInfo;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestOperationInfo {

    private OperationInfo operationInfo = null;

    @Before
    public void setUp() throws Exception {
        operationInfo = new OperationInfo();
        operationInfo.setDescription("DescOfOperation");
        operationInfo.setImpact("Unknow");
        operationInfo.setName("NameOfOperation");
        operationInfo.setReturnType("ReturnTypeUnkown");
        operationInfo.setRole("UntrustedRole");
        operationInfo.setType("Operation");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSetImpact() {
        operationInfo.setImpact("Unkown");
        MBeanOperationInfo operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("Unkown".toUpperCase(), operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.UNKNOWN, operationBean.getImpact());

        operationInfo.setImpact("ACTION");
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("ACTION", operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.ACTION, operationBean.getImpact());

        operationInfo.setImpact("ACTION_INFO");
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("ACTION_INFO", operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.ACTION_INFO, operationBean.getImpact());

        operationInfo.setImpact("INFO");
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("INFO", operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.INFO, operationBean.getImpact());

        operationInfo.setImpact("info");
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("info".toUpperCase(), operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.INFO, operationBean.getImpact());

        operationInfo.setImpact(null);
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals(null, operationInfo.getImpact());
        Assert.assertEquals(MBeanOperationInfo.UNKNOWN, operationBean.getImpact());

    }

    @Test
    public void testSetRole() {
        operationInfo.setRole("UntrustedRole");
        Assert.assertEquals("UntrustedRole", operationInfo.getRole());

        operationInfo.setRole("getter");
        Assert.assertEquals("getter", operationInfo.getRole());

        operationInfo.setRole("constructor");
        Assert.assertEquals("constructor", operationInfo.getRole());
    }

    @Test
    public void testSetReturnType() {
        operationInfo.setReturnType(String.class.getName());
        MBeanOperationInfo operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals(String.class.getName(), operationInfo.getReturnType());
        Assert.assertEquals(String.class.getName(), operationBean.getReturnType());

        operationInfo.setReturnType(Void.TYPE.getName());
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals(Void.TYPE.getName(), operationInfo.getReturnType());
        Assert.assertEquals(Void.TYPE.getName(), operationBean.getReturnType());

        operationInfo.setReturnType(TestOperationInfo.class.getName());
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals(TestOperationInfo.class.getName(), operationInfo.getReturnType());
        Assert.assertEquals(TestOperationInfo.class.getName(), operationBean.getReturnType());

        operationInfo.setReturnType(null);
        operationBean = (MBeanOperationInfo) operationInfo.getLatestMBeanInfo();
        Assert.assertEquals("void", operationInfo.getReturnType());
        Assert.assertEquals("void", operationBean.getReturnType());

    }

}
