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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.ext.EntityResolver2;

/**
 * Wrapper class around the Digester that hide Digester's initialization
 * details.
 */
public class DigesterFactory {

    /**
     * Mapping of well-known public IDs used by the Servlet API to the matching
     * local resource.
     */
    public static final Map<String,String> SERVLET_API_PUBLIC_IDS;

    /**
     * Mapping of well-known system IDs used by the Servlet API to the matching
     * local resource.
     */
    public static final Map<String,String> SERVLET_API_SYSTEM_IDS;

    static {
        Map<String, String> publicIds = new HashMap<>();
        Map<String, String> systemIds = new HashMap<>();

        // W3C
        publicIds.put(XmlIdentifiers.XSD_10_PUBLIC, idFor("XMLSchema.dtd"));
        publicIds.put(XmlIdentifiers.DATATYPES_PUBLIC, idFor("datatypes.dtd"));
        systemIds.put(XmlIdentifiers.XML_2001_XSD, idFor("xml.xsd"));

        // from J2EE 1.2
        publicIds.put(XmlIdentifiers.WEB_22_PUBLIC, idFor("web-app_2_2.dtd"));
        publicIds.put(XmlIdentifiers.TLD_11_PUBLIC, idFor("web-jsptaglibrary_1_1.dtd"));

        // from J2EE 1.3
        publicIds.put(XmlIdentifiers.WEB_23_PUBLIC, idFor("web-app_2_3.dtd"));
        publicIds.put(XmlIdentifiers.TLD_12_PUBLIC, idFor("web-jsptaglibrary_1_2.dtd"));

        // from J2EE 1.4
        systemIds.put("http://www.ibm.com/webservices/xsd/j2ee_web_services_1_1.xsd",
                idFor("j2ee_web_services_1_1.xsd"));
        systemIds.put("http://www.ibm.com/webservices/xsd/j2ee_web_services_client_1_1.xsd",
                idFor("j2ee_web_services_client_1_1.xsd"));
        systemIds.put(XmlIdentifiers.WEB_24_XSD, idFor("web-app_2_4.xsd"));
        systemIds.put(XmlIdentifiers.TLD_20_XSD, idFor("web-jsptaglibrary_2_0.xsd"));
        addSelf(systemIds, "j2ee_1_4.xsd");
        addSelf(systemIds, "jsp_2_0.xsd");

        // from JavaEE 5
        systemIds.put(XmlIdentifiers.WEB_25_XSD, idFor("web-app_2_5.xsd"));
        systemIds.put(XmlIdentifiers.TLD_21_XSD, idFor("web-jsptaglibrary_2_1.xsd"));
        addSelf(systemIds, "javaee_5.xsd");
        addSelf(systemIds, "jsp_2_1.xsd");
        addSelf(systemIds, "javaee_web_services_1_2.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_2.xsd");

        // from JavaEE 6
        systemIds.put(XmlIdentifiers.WEB_30_XSD, idFor("web-app_3_0.xsd"));
        systemIds.put(XmlIdentifiers.WEB_FRAGMENT_30_XSD, idFor("web-fragment_3_0.xsd"));
        addSelf(systemIds, "web-common_3_0.xsd");
        addSelf(systemIds, "javaee_6.xsd");
        addSelf(systemIds, "jsp_2_2.xsd");
        addSelf(systemIds, "javaee_web_services_1_3.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_3.xsd");

        // from JavaEE 7
        systemIds.put(XmlIdentifiers.WEB_31_XSD, idFor("web-app_3_1.xsd"));
        systemIds.put(XmlIdentifiers.WEB_FRAGMENT_31_XSD, idFor("web-fragment_3_1.xsd"));
        addSelf(systemIds, "web-common_3_1.xsd");
        addSelf(systemIds, "javaee_7.xsd");
        addSelf(systemIds, "jsp_2_3.xsd");
        addSelf(systemIds, "javaee_web_services_1_4.xsd");
        addSelf(systemIds, "javaee_web_services_client_1_4.xsd");

        SERVLET_API_PUBLIC_IDS = Collections.unmodifiableMap(publicIds);
        SERVLET_API_SYSTEM_IDS = Collections.unmodifiableMap(systemIds);
    }

    private static void addSelf(Map<String, String> ids, String id) {
        String systemId = idFor(id);
        ids.put(systemId, systemId);
    }

    private static String idFor(String url) {
        return ServletContext.class.getResource("resources/" + url).toExternalForm();
    }


    /**
     * Create a <code>Digester</code> parser.
     * @param xmlValidation turn on/off xml validation
     * @param xmlNamespaceAware turn on/off namespace validation
     * @param rule an instance of <code>RuleSet</code> used for parsing the xml.
     * @param blockExternal turn on/off the blocking of external resources
     */
    public static Digester newDigester(boolean xmlValidation,
                                       boolean xmlNamespaceAware,
                                       RuleSet rule,
                                       boolean blockExternal) {
        Digester digester = new Digester();
        digester.setNamespaceAware(xmlNamespaceAware);
        digester.setValidating(xmlValidation);
        digester.setUseContextClassLoader(true);
        EntityResolver2 resolver = new LocalResolver(SERVLET_API_PUBLIC_IDS,
                SERVLET_API_SYSTEM_IDS, blockExternal);
        digester.setEntityResolver(resolver);
        if (rule != null) {
            digester.addRuleSet(rule);
        }

        return digester;
    }
}
