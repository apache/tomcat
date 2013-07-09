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
package org.apache.tomcat.util.descriptor;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;


/**
 * Wrapper class around the Digester that hide Digester's initialization
 * details.
 */
public class DigesterFactory {

    /**
     * A resolver for the resources packaged in servlet-api.jar
     */
    public static final LocalResolver SERVLET_RESOLVER;


    static {
        Map<String, String> publicIds = new HashMap<>();
        Map<String, String> systemIds = new HashMap<>();

        // W3C
        publicIds.put(XmlIdentifiers.XSD_10_PUBLIC,
                urlFor("/javax/servlet/resources/XMLSchema.dtd"));
        publicIds.put(XmlIdentifiers.DATATYPES_PUBLIC,
                urlFor("/javax/servlet/resources/datatypes.dtd"));
        systemIds.put(XmlIdentifiers.XML_2001_XSD,
                urlFor("/javax/servlet/resources/xml.xsd"));

        // from J2EE 1.2
        publicIds.put(XmlIdentifiers.WEB_22_PUBLIC,
                urlFor("/javax/servlet/resources/web-app_2_2.dtd"));
        publicIds.put(XmlIdentifiers.TLD_11_PUBLIC,
                urlFor("/javax/servlet/resources/web-jsptaglibrary_1_1.dtd"));

        // from J2EE 1.3
        publicIds.put(XmlIdentifiers.WEB_23_PUBLIC,
                urlFor("/javax/servlet/resources/web-app_2_3.dtd"));
        publicIds.put(XmlIdentifiers.TLD_12_PUBLIC,
                urlFor("/javax/servlet/resources/web-jsptaglibrary_1_2.dtd"));

        // from J2EE 1.4
        systemIds.put("http://www.ibm.com/webservices/xsd/j2ee_web_services_client_1_1.xsd",
                urlFor("/javax/servlet/resources/j2ee_web_services_client_1_1.xsd"));
        systemIds.put(XmlIdentifiers.WEB_24_XSD,
                urlFor("/javax/servlet/resources/web-app_2_4.xsd"));
        systemIds.put(XmlIdentifiers.TLD_20_XSD,
                urlFor("/javax/servlet/resources/web-jsptaglibrary_2_0.xsd"));

        // from JavaEE 5
        systemIds.put(XmlIdentifiers.WEB_25_XSD,
                urlFor("/javax/servlet/resources/web-app_2_5.xsd"));
        systemIds.put(XmlIdentifiers.TLD_21_XSD,
                urlFor("/javax/servlet/resources/web-jsptaglibrary_2_1.xsd"));

        // from JavaEE 6
        systemIds.put(XmlIdentifiers.WEB_30_XSD,
                urlFor("/javax/servlet/resources/web-app_3_0.xsd"));
        systemIds.put(XmlIdentifiers.WEB_FRAGMENT_30_XSD,
                urlFor("/javax/servlet/resources/web-fragment_3_0.xsd"));

        // from JavaEE 7
        systemIds.put(XmlIdentifiers.WEB_31_XSD,
                urlFor("/javax/servlet/resources/web-app_3_1.xsd"));
        systemIds.put(XmlIdentifiers.WEB_FRAGMENT_31_XSD,
                urlFor("/javax/servlet/resources/web-fragment_3_1.xsd"));

        SERVLET_RESOLVER =
                new LocalResolver(Servlet.class, publicIds, systemIds);
    }


    private static String urlFor(String file) {
        return DigesterFactory.class.getResource(file).toExternalForm();
    }


    /**
     * Create a <code>Digester</code> parser.
     * @param xmlValidation turn on/off xml validation
     * @param xmlNamespaceAware turn on/off namespace validation
     * @param rule an instance of <code>RuleSet</code> used for parsing the xml.
     */
    public static Digester newDigester(boolean xmlValidation,
                                       boolean xmlNamespaceAware,
                                       RuleSet rule) {
        Digester digester = new Digester();
        digester.setNamespaceAware(xmlNamespaceAware);
        digester.setValidating(xmlValidation);
        digester.setUseContextClassLoader(true);
        digester.setEntityResolver(SERVLET_RESOLVER);
        if ( rule != null ) {
            digester.addRuleSet(rule);
        }

        return (digester);
    }
}
