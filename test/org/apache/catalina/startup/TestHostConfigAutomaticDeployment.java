/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeployment extends TomcatBaseTest {

    private static final ContextName  APP_NAME = new ContextName("myapp");
    private static final File XML_SOURCE =
            new File("test/deployment/context.xml");

    private File external;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        external = new File(getTemporaryDirectory(), "external");
        if (!external.exists() && !external.mkdir()) {
            Assert.fail("Unable to create external for test");
        }

        // Disable background thread
        tomcat.getEngine().setBackgroundProcessorDelay(-1);

        // Enable deployer
        tomcat.getHost().addLifecycleListener(new HostConfig());

        // Disable deployment on start up
        tomcat.getHost().setDeployOnStartup(false);

        // Clean-up after test
        addDeleteOnTearDown(new File(tomcat.basedir, "/conf"));
        addDeleteOnTearDown(external);
    }

    @Test
    public void testDeploymentXmlFFF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlFFT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, false, false,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, false, true,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, true, false,
                LifecycleState.FAILED, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, true, true,
                LifecycleState.FAILED, true, false, false);
    }

    private void initTestDeploymentXml() throws IOException {
        File dest = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                APP_NAME + ".xml");
        File parent = dest.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }

        Files.copy(XML_SOURCE.toPath(), dest.toPath());
    }

    private void doTestDeployment(boolean deployXML, boolean copyXML,
            boolean unpackWARs, LifecycleState resultState, boolean resultXml,
            boolean resultWar, boolean resultDir) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Start the instance
        tomcat.start();

        // Set the attributes
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setDeployXML(deployXML);
        host.setCopyXML(copyXML);
        host.setUnpackWARs(unpackWARs);

        // Trigger automatic deployment
        host.backgroundProcess();

        // Test the results
        Container ctxt = tomcat.getHost().findChild(APP_NAME.getPath());
        if (resultState == null) {
            Assert.assertNull(ctxt);
        } else {
            Assert.assertNotNull(ctxt);
            Assert.assertEquals(resultState, ctxt.getState());
        }

        File xml = new File(
                getConfigBaseFile(host), APP_NAME.getBaseName() + ".xml");
        Assert.assertEquals(
                Boolean.valueOf(resultXml), Boolean.valueOf(xml.isFile()));

        File war = new File(
                getAppBaseFile(host), APP_NAME.getBaseName() + ".war");
        Assert.assertEquals(
                Boolean.valueOf(resultWar), Boolean.valueOf(war.isFile()));

        File dir = new File(host.getAppBase(), APP_NAME.getBaseName());
        Assert.assertEquals(
                Boolean.valueOf(resultDir), Boolean.valueOf(dir.isDirectory()));

    }
    
    
    // Static methods to compensate for methods that are present in 8.0.x but
    // not in 7.0.x
    
    private static File getConfigBaseFile(Host host) {
        String path = null;
        if (host.getXmlBase() != null) {
            path = host.getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = host.getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(host.getName());
            path = xmlDir.toString();
        }
        
        return getCanonicalPath(path);
    }
    
    private static File getAppBaseFile(Host host) {
        return getCanonicalPath(host.getAppBase());
    }
 
    private static File getCanonicalPath(String path) {
        File file = new File(path);
        File base = new File(System.getProperty(Globals.CATALINA_BASE_PROP));
        if (!file.isAbsolute())
            file = new File(base,path);
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
}
