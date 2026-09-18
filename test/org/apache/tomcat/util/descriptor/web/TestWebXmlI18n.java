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
package org.apache.tomcat.util.descriptor.web;

import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.xml.sax.InputSource;

public class TestWebXmlI18n {

    private static final String WEB_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"\n"
            + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee "
            + "https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd\"\n"
            + "         version=\"6.0\">\n"
            + "  <description>App default description</description>\n"
            + "  <description xml:lang=\"zh\">应用描述</description>\n"
            + "  <display-name>Default name</display-name>\n"
            + "  <display-name xml:lang=\"zh\">默认名称</display-name>\n"
            + "  <filter>\n"
            + "    <description>Filter default description</description>\n"
            + "    <description xml:lang=\"de\">Filter-Beschreibung</description>\n"
            + "    <display-name>Filter default name</display-name>\n"
            + "    <display-name xml:lang=\"de\">Filter-Anzeigename</display-name>\n"
            + "    <filter-name>f1</filter-name>\n"
            + "    <filter-class>org.apache.catalina.filters.SetCharacterEncodingFilter</filter-class>\n"
            + "  </filter>\n"
            + "  <filter-mapping>\n"
            + "    <filter-name>f1</filter-name>\n"
            + "    <url-pattern>/*</url-pattern>\n"
            + "  </filter-mapping>\n"
            + "  <servlet>\n"
            + "    <description>Servlet default description</description>\n"
            + "    <description xml:lang=\"fr\">Description du servlet</description>\n"
            + "    <display-name>Servlet default name</display-name>\n"
            + "    <display-name xml:lang=\"fr\">Nom du servlet</display-name>\n"
            + "    <servlet-name>s1</servlet-name>\n"
            + "    <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>\n"
            + "    <security-role-ref>\n"
            + "      <description>Role ref default description</description>\n"
            + "      <description xml:lang=\"it\">Descrizione del ruolo</description>\n"
            + "      <role-name>admin</role-name>\n"
            + "      <role-link>manager</role-link>\n"
            + "    </security-role-ref>\n"
            + "  </servlet>\n"
            + "  <servlet-mapping>\n"
            + "    <servlet-name>s1</servlet-name>\n"
            + "    <url-pattern>/</url-pattern>\n"
            + "  </servlet-mapping>\n"
            + "  <security-constraint>\n"
            + "    <description>Constraint default description</description>\n"
            + "    <description xml:lang=\"es\">Descripción de la restricción</description>\n"
            + "    <display-name>Constraint default name</display-name>\n"
            + "    <display-name xml:lang=\"es\">Nombre de la restricción</display-name>\n"
            + "    <web-resource-collection>\n"
            + "      <description>Collection default description</description>\n"
            + "      <description xml:lang=\"ja\">コレクションの説明</description>\n"
            + "      <web-resource-name>wrc1</web-resource-name>\n"
            + "      <url-pattern>/protected/*</url-pattern>\n"
            + "    </web-resource-collection>\n"
            + "    <auth-constraint>\n"
            + "      <role-name>admin</role-name>\n"
            + "    </auth-constraint>\n"
            + "  </security-constraint>\n"
            + "  <env-entry>\n"
            + "    <description>Env entry default description</description>\n"
            + "    <description xml:lang=\"pt\">Descrição do ambiente</description>\n"
            + "    <env-entry-name>env1</env-entry-name>\n"
            + "    <env-entry-type>java.lang.String</env-entry-type>\n"
            + "    <env-entry-value>value1</env-entry-value>\n"
            + "  </env-entry>\n"
            + "  <resource-ref>\n"
            + "    <description>Resource ref default description</description>\n"
            + "    <description xml:lang=\"ru\">Описание ресурса</description>\n"
            + "    <res-ref-name>jdbc/TestDB</res-ref-name>\n"
            + "    <res-type>javax.sql.DataSource</res-type>\n"
            + "  </resource-ref>\n"
            + "  <message-destination>\n"
            + "    <description>Message destination default description</description>\n"
            + "    <description xml:lang=\"ko\">메시지 대상 설명</description>\n"
            + "    <display-name>Message destination default name</display-name>\n"
            + "    <display-name xml:lang=\"ko\">메시지 대상 이름</display-name>\n"
            + "    <message-destination-name>md1</message-destination-name>\n"
            + "  </message-destination>\n"
            + "</web-app>\n";

    @Test
    public void testParseWebAppDescriptionAndDisplayName() throws Exception {
        WebXml webXml = parse(WEB_XML);

        List<LocaleElement> descriptions = webXml.getDescriptions();
        Assert.assertEquals(2, descriptions.size());
        Assert.assertEquals("App default description", descriptions.get(0).getContent());
        Assert.assertNull(descriptions.get(0).getLang());
        Assert.assertEquals("应用描述", descriptions.get(1).getContent());
        Assert.assertEquals("zh", descriptions.get(1).getLang());

        List<LocaleElement> displayNames = webXml.getDisplayNames();
        Assert.assertEquals(2, displayNames.size());
        Assert.assertEquals("Default name", displayNames.get(0).getContent());
        Assert.assertNull(displayNames.get(0).getLang());
        Assert.assertEquals("默认名称", displayNames.get(1).getContent());
        Assert.assertEquals("zh", displayNames.get(1).getLang());

        // The default (language-less) values must be returned by the compatibility getters
        Assert.assertEquals("App default description", webXml.getDescription());
        Assert.assertEquals("Default name", webXml.getDisplayName());
    }

    @Test
    public void testParseFilterDescriptionAndDisplayName() throws Exception {
        WebXml webXml = parse(WEB_XML);

        FilterDef filter = webXml.getFilters().get("f1");
        Assert.assertNotNull(filter);

        List<LocaleElement> descriptions = filter.getDescriptions();
        Assert.assertEquals(2, descriptions.size());
        Assert.assertEquals("Filter default description", descriptions.get(0).getContent());
        Assert.assertEquals("Filter-Beschreibung", descriptions.get(1).getContent());
        Assert.assertEquals("de", descriptions.get(1).getLang());

        List<LocaleElement> displayNames = filter.getDisplayNames();
        Assert.assertEquals(2, displayNames.size());
        Assert.assertEquals("Filter default name", displayNames.get(0).getContent());
        Assert.assertEquals("Filter-Anzeigename", displayNames.get(1).getContent());
        Assert.assertEquals("de", displayNames.get(1).getLang());

        Assert.assertEquals("Filter default description", filter.getDescription());
        Assert.assertEquals("Filter default name", filter.getDisplayName());
    }

    @Test
    public void testParseServletDescriptionDisplayNameAndRoleRef() throws Exception {
        WebXml webXml = parse(WEB_XML);

        ServletDef servlet = webXml.getServlets().get("s1");
        Assert.assertNotNull(servlet);

        List<LocaleElement> descriptions = servlet.getDescriptions();
        Assert.assertEquals(2, descriptions.size());
        Assert.assertEquals("Servlet default description", descriptions.get(0).getContent());
        Assert.assertEquals("Description du servlet", descriptions.get(1).getContent());
        Assert.assertEquals("fr", descriptions.get(1).getLang());

        List<LocaleElement> displayNames = servlet.getDisplayNames();
        Assert.assertEquals(2, displayNames.size());
        Assert.assertEquals("Servlet default name", displayNames.get(0).getContent());
        Assert.assertEquals("Nom du servlet", displayNames.get(1).getContent());
        Assert.assertEquals("fr", displayNames.get(1).getLang());

        Assert.assertEquals(1, servlet.getSecurityRoleRefs().size());
        SecurityRoleRef roleRef = servlet.getSecurityRoleRefs().iterator().next();
        List<LocaleElement> roleRefDescriptions = roleRef.getDescriptions();
        Assert.assertEquals(2, roleRefDescriptions.size());
        Assert.assertEquals("Role ref default description", roleRefDescriptions.get(0).getContent());
        Assert.assertEquals("Descrizione del ruolo", roleRefDescriptions.get(1).getContent());
        Assert.assertEquals("it", roleRefDescriptions.get(1).getLang());
    }

    @Test
    public void testParseSecurityConstraintAndCollection() throws Exception {
        WebXml webXml = parse(WEB_XML);

        Assert.assertEquals(1, webXml.getSecurityConstraints().size());
        SecurityConstraint constraint = webXml.getSecurityConstraints().iterator().next();

        List<LocaleElement> constraintDescriptions = constraint.getDescriptions();
        Assert.assertEquals(2, constraintDescriptions.size());
        Assert.assertEquals("Constraint default description", constraintDescriptions.get(0).getContent());
        Assert.assertEquals("Descripción de la restricción", constraintDescriptions.get(1).getContent());
        Assert.assertEquals("es", constraintDescriptions.get(1).getLang());

        List<LocaleElement> constraintDisplayNames = constraint.getDisplayNames();
        Assert.assertEquals(2, constraintDisplayNames.size());
        Assert.assertEquals("Constraint default name", constraintDisplayNames.get(0).getContent());
        Assert.assertEquals("Nombre de la restricción", constraintDisplayNames.get(1).getContent());
        Assert.assertEquals("es", constraintDisplayNames.get(1).getLang());

        Assert.assertEquals(1, constraint.findCollections().length);
        SecurityCollection collection = constraint.findCollections()[0];
        List<LocaleElement> collectionDescriptions = collection.getDescriptions();
        Assert.assertEquals(2, collectionDescriptions.size());
        Assert.assertEquals("Collection default description", collectionDescriptions.get(0).getContent());
        Assert.assertEquals("コレクションの説明", collectionDescriptions.get(1).getContent());
        Assert.assertEquals("ja", collectionDescriptions.get(1).getLang());
    }

    @Test
    public void testParseResourceBaseDescriptions() throws Exception {
        WebXml webXml = parse(WEB_XML);

        ContextEnvironment envEntry = webXml.getEnvEntries().get("env1");
        Assert.assertNotNull(envEntry);
        Assert.assertEquals(2, envEntry.getDescriptions().size());
        Assert.assertEquals("Env entry default description", envEntry.getDescriptions().get(0).getContent());
        Assert.assertEquals("Descrição do ambiente", envEntry.getDescriptions().get(1).getContent());
        Assert.assertEquals("pt", envEntry.getDescriptions().get(1).getLang());

        ContextResource resource = webXml.getResourceRefs().get("jdbc/TestDB");
        Assert.assertNotNull(resource);
        Assert.assertEquals(2, resource.getDescriptions().size());
        Assert.assertEquals("Resource ref default description", resource.getDescriptions().get(0).getContent());
        Assert.assertEquals("Описание ресурса", resource.getDescriptions().get(1).getContent());
        Assert.assertEquals("ru", resource.getDescriptions().get(1).getLang());

        MessageDestination messageDestination = webXml.getMessageDestinations().get("md1");
        Assert.assertNotNull(messageDestination);
        Assert.assertEquals(2, messageDestination.getDescriptions().size());
        Assert.assertEquals("Message destination default description",
                messageDestination.getDescriptions().get(0).getContent());
        Assert.assertEquals("메시지 대상 설명", messageDestination.getDescriptions().get(1).getContent());
        Assert.assertEquals("ko", messageDestination.getDescriptions().get(1).getLang());
        Assert.assertEquals(2, messageDestination.getDisplayNames().size());
        Assert.assertEquals("Message destination default name",
                messageDestination.getDisplayNames().get(0).getContent());
        Assert.assertEquals("메시지 대상 이름", messageDestination.getDisplayNames().get(1).getContent());
        Assert.assertEquals("ko", messageDestination.getDisplayNames().get(1).getLang());
    }

    @Test
    public void testToXmlRoundTrip() throws Exception {
        WebXml webXml = parse(WEB_XML);

        // Serialize and parse again
        WebXml parsed = parse(webXml.toXml());

        Assert.assertEquals(webXml.getDescriptions(), parsed.getDescriptions());
        Assert.assertEquals(webXml.getDisplayNames(), parsed.getDisplayNames());

        FilterDef filter = parsed.getFilters().get("f1");
        Assert.assertNotNull(filter);
        Assert.assertEquals(webXml.getFilters().get("f1").getDescriptions(), filter.getDescriptions());
        Assert.assertEquals(webXml.getFilters().get("f1").getDisplayNames(), filter.getDisplayNames());

        ServletDef servlet = parsed.getServlets().get("s1");
        Assert.assertNotNull(servlet);
        Assert.assertEquals(webXml.getServlets().get("s1").getDescriptions(), servlet.getDescriptions());
        Assert.assertEquals(webXml.getServlets().get("s1").getDisplayNames(), servlet.getDisplayNames());
    }

    @Test
    public void testSetDescriptionAndSetDisplayNameReplaceAll() {
        WebXml webXml = new WebXml();
        webXml.addDescription(new LocaleElement("App description", null));
        webXml.addDescription(new LocaleElement("应用描述", "zh"));

        // The compatibility setter must replace all existing entries
        webXml.setDescription("New description");
        Assert.assertEquals(1, webXml.getDescriptions().size());
        Assert.assertEquals("New description", webXml.getDescription());

        webXml.addDisplayName(new LocaleElement("App name", null));
        webXml.addDisplayName(new LocaleElement("默认名称", "zh"));
        webXml.setDisplayName("New name");
        Assert.assertEquals(1, webXml.getDisplayNames().size());
        Assert.assertEquals("New name", webXml.getDisplayName());
    }

    @Test
    public void testMergeDisplayNameDifferentLanguages() throws Exception {
        WebXml main = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.setName("fragment1");
        fragment1.setURL(url("file:///fragment1"));
        fragment1.addDisplayName(new LocaleElement("Name in German", "de"));

        WebXml fragment2 = new WebXml();
        fragment2.setName("fragment2");
        fragment2.setURL(url("file:///fragment2"));
        fragment2.addDisplayName(new LocaleElement("Name in French", "fr"));

        Assert.assertTrue(main.merge(new HashSet<>(Arrays.asList(fragment1, fragment2))));

        // Both language specific display names must be merged
        Assert.assertEquals(2, main.getDisplayNames().size());
        Assert.assertEquals("Name in German", findContent(main.getDisplayNames(), "de"));
        Assert.assertEquals("Name in French", findContent(main.getDisplayNames(), "fr"));
    }

    @Test
    public void testMergeDisplayNameConflict() throws Exception {
        WebXml main = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.setName("fragment1");
        fragment1.setURL(url("file:///fragment1"));
        fragment1.addDisplayName(new LocaleElement("Name one", "en"));

        WebXml fragment2 = new WebXml();
        fragment2.setName("fragment2");
        fragment2.setURL(url("file:///fragment2"));
        fragment2.addDisplayName(new LocaleElement("Name two", "en"));

        Assert.assertFalse(main.merge(new HashSet<>(Arrays.asList(fragment1, fragment2))));
    }

    @Test
    public void testMergeDescriptionDifferentLanguages() throws Exception {
        WebXml main = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.setName("fragment1");
        fragment1.setURL(url("file:///fragment1"));
        fragment1.addDescription(new LocaleElement("Beschreibung auf Deutsch", "de"));

        WebXml fragment2 = new WebXml();
        fragment2.setName("fragment2");
        fragment2.setURL(url("file:///fragment2"));
        fragment2.addDescription(new LocaleElement("Description en français", "fr"));

        Assert.assertTrue(main.merge(new HashSet<>(Arrays.asList(fragment1, fragment2))));

        Assert.assertEquals(2, main.getDescriptions().size());
        Assert.assertEquals("Beschreibung auf Deutsch", findContent(main.getDescriptions(), "de"));
        Assert.assertEquals("Description en français", findContent(main.getDescriptions(), "fr"));
    }

    @Test
    public void testMergeDescriptionConflict() throws Exception {
        WebXml main = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.setName("fragment1");
        fragment1.setURL(url("file:///fragment1"));
        fragment1.addDescription(new LocaleElement("Description one", "en"));

        WebXml fragment2 = new WebXml();
        fragment2.setName("fragment2");
        fragment2.setURL(url("file:///fragment2"));
        fragment2.addDescription(new LocaleElement("Description two", "en"));

        Assert.assertFalse(main.merge(new HashSet<>(Arrays.asList(fragment1, fragment2))));
    }

    private static WebXml parse(String xml) throws Exception {
        WebXmlParser parser = new WebXmlParser(true, false, false);
        WebXml webXml = new WebXml();
        Assert.assertTrue(parser.parseWebXml(new InputSource(new StringReader(xml)), webXml, false));
        return webXml;
    }

    private static URL url(String spec) throws Exception {
        return new URL(spec);
    }

    private static String findContent(List<LocaleElement> elements, String lang) {
        for (LocaleElement element : elements) {
            if (lang.equals(element.getLang())) {
                return element.getContent();
            }
        }
        return null;
    }
}