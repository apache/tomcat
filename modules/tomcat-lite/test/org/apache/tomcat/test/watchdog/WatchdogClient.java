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
package org.apache.tomcat.test.watchdog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class WatchdogClient {

  protected String goldenDir;
  protected String testMatch;
  protected String file;
  protected String[] exclude = null;
  protected String[] slow =
  {
      "SingleModelTest" // slow
  };

  protected String targetMatch;

  protected int port;

  Properties props = new Properties();

  protected void beforeSuite() {
  }

  protected void afterSuite(TestResult res) {
  }

  public Test getSuite() {
      return getSuite(port);
  }

  public static class NullResolver implements EntityResolver {
      public InputSource resolveEntity (String publicId,
                                                 String systemId)
          throws SAXException, IOException
      {
          return new InputSource(new StringReader(""));
      }
  }

  /** Read XML as DOM.
   */
  public static Document readXml(InputStream is)
      throws SAXException, IOException, ParserConfigurationException
  {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setValidating(false);
      dbf.setIgnoringComments(false);
      dbf.setIgnoringElementContentWhitespace(true);
      DocumentBuilder db = null;
      db = dbf.newDocumentBuilder();
      db.setEntityResolver( new NullResolver() );
      Document doc = db.parse(is);
      return doc;
  }

  /**
   * Return a test suite for running a watchdog-like
   * test file.
   *
   * @param base base dir for the watchdog dir
   * @param testMatch Prefix of tests to be run
   * @return
   */
  public Test getSuite(int port) {
    TestSuite tests = new WatchdogTests();
    tests.setName(this.getClass().getSimpleName());

    props.setProperty("port", Integer.toString(port));
    props.setProperty("host", "localhost");
    props.setProperty("wgdir",
        goldenDir);


    try {
      Document doc = readXml(new FileInputStream(file));
      Element docE = doc.getDocumentElement();
      NodeList targetsL = docE.getElementsByTagName("target");
      for (int i = 0; i < targetsL.getLength(); i++) {
        Element target = (Element) targetsL.item(i);
        String targetName = target.getAttribute("name");
        if (targetMatch != null && !targetName.equals(targetMatch)) {
            continue;
        }

        // Tests are duplicated
        //TestSuite targetSuite = new TestSuite(targetName);

        NodeList watchDogL = target.getElementsByTagName("watchdog");
        for (int j = 0; j < watchDogL.getLength(); j++) {
          Element watchE = (Element) watchDogL.item(j);
          String testName = watchE.getAttribute("testName");
          if (single != null && !testName.equals(single)) {
              continue;
          }
          if (testMatch != null) {
              if (!testName.startsWith(testMatch)) {
                  continue;
              }
          }
          if (exclude != null) {
              boolean found = false;
              for (String e: exclude) {
                  if (e.equals(testName)) {
                      found = true;
                      break;
                  }
              }
              if (found) {
                  continue;
              }
          }
          testName = testName + "(" + this.getClass().getName() + ")";
          WatchdogTestCase test = new WatchdogTestCase(watchE, props, testName);
          tests.addTest(test);
          if (single != null) {
              singleTest = test;
              break;
          }
        }
      }

    } catch (IOException e) {
        e.printStackTrace();
    } catch (SAXException e) {
        e.printStackTrace();
    } catch (ParserConfigurationException e) {
        e.printStackTrace();
    }
    return tests;
  }

  // --------- Inner classes -------------

  protected String getWatchdogdir() {
      String path = System.getProperty("watchdog.home");
      if (path != null) {
          return path;
      }
      path = "..";
      for (int i = 0; i < 10; i++) {
          File f = new File(path + "/watchdog");
          if (f.exists()) {
              return f.getAbsolutePath();
          }
          path = path + "/..";
      }
      return null;
  }

  public class WatchdogTests extends TestSuite {
      public void run(TestResult res) {
          beforeSuite();
          super.run(res);
          afterSuite(res);
      }
  }

  // Support for running a single test in the suite

  protected String single;
  WatchdogTestCase singleTest;

  public int countTestCases() {
      return 1;
  }

  public void run(TestResult result) {
      getSuite();
      if (singleTest != null) {
          beforeSuite();
          singleTest.run(result);
          afterSuite(result);
      }
  }

}
