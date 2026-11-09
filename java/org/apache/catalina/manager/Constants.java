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
package org.apache.catalina.manager;

import java.util.Locale;

import org.apache.tomcat.util.res.StringManager;

/**
 * Constants used by the manager web application.
 */
public class Constants {

    /**
     * Prevents instantiation.
     */
    private Constants() {
    }

    /**
     * The fully qualified name of this package.
     */
    public static final String Package = "org.apache.catalina.manager";

    /**
     * The HTML rel attribute value for external links.
     */
    public static final String REL_EXTERNAL = "rel=\"noopener noreferrer\"";

    /**
     * The copyright date range for the manager application.
     */
    public static final String COPYRIGHT_DATE_RANGE;

    /**
     * The plain text copyright notice for the manager application.
     */
    public static final String COPYRIGHT_NOTICE;

    /**
     * The HTML-encoded copyright notice for the manager application.
     */
    public static final String HTML_COPYRIGHT_NOTICE;

    /**
     * The HTML header section template for manager pages. The parameters are the context path of the web application
     * ({@code {0}}) and the language tag of the page ({@code {1}}, see {@link #getLanguageTag(StringManager)}).
     */
    public static final String HTML_HEADER_SECTION;

    /**
     * The HTML body header section template for manager pages.
     */
    public static final String BODY_HEADER_SECTION;

    /**
     * The HTML message section template for manager pages.
     */
    public static final String MESSAGE_SECTION;

    /**
     * The HTML manager section template for manager pages.
     */
    public static final String MANAGER_SECTION;

    /**
     * The HTML server header section template for manager pages.
     */
    public static final String SERVER_HEADER_SECTION;

    /**
     * The HTML server row section template for manager pages.
     */
    public static final String SERVER_ROW_SECTION;

    /**
     * The HTML tail section template for manager pages.
     */
    public static final String HTML_TAIL_SECTION;

    static {
        //@formatter:off
        COPYRIGHT_DATE_RANGE = "1999-2026";

        COPYRIGHT_NOTICE = "Copyright (c) " + COPYRIGHT_DATE_RANGE + ", Apache Software Foundation";
        HTML_COPYRIGHT_NOTICE = "Copyright &copy; " + COPYRIGHT_DATE_RANGE + ", Apache Software Foundation";

        HTML_HEADER_SECTION =
                "<!DOCTYPE html>\n" +
                "<html lang=\"{1}\">\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "<link href=\"{0}/images/favicon.ico\" rel=\"icon\" type=\"image/x-icon\" />\n" +
                "<link rel=\"stylesheet\" href=\"{0}/css/manager.css\">\n";

        BODY_HEADER_SECTION =
                "<title>{0}</title>\n" +
                "</head>\n" +
                "\n" +
                "<body>\n" +
                "\n" +
                "<table>\n" +
                " <tr>\n" +
                "  <td colspan=\"2\">\n" +
                "   <a href=\"https://tomcat.apache.org/\" " + REL_EXTERNAL + ">\n" +
                "    <img class='tomcat-logo' alt=\"The Tomcat Servlet/JSP Container\"\n" +
                "         src=\"{0}/images/tomcat.svg\">\n" +
                "   </a>\n" +
                "   <a href=\"https://www.apache.org/\" " + REL_EXTERNAL + ">\n" +
                "    <img class=\"asf-logo\" alt=\"The Apache Software Foundation\"\n" +
                "         src=\"{0}/images/asf-logo.svg\">\n" +
                "   </a>\n" +
                "  </td>\n" +
                " </tr>\n" +
                "</table>\n" +
                "<hr class=\"line\">\n" +
                "<table>\n" +
                " <tr>\n" +
                "  <td class=\"page-title\">{1}</td>\n" +
                " </tr>\n" +
                "</table>\n" +
                "<br>\n" +
                "\n";

        MESSAGE_SECTION =
                "<table class=\"data-table\">\n" +
                " <tr>\n" +
                "  <td class=\"row-left message-label\">" +
                "<small><strong>{0}</strong></small>&nbsp;</td>\n" +
                "  <td class=\"row-left\"><pre>{1}</pre></td>\n" +
                " </tr>\n" +
                "</table>\n" +
                "<br>\n" +
                "\n";

        MANAGER_SECTION =
                "<table class=\"data-table\">\n" +
                "<tr>\n" +
                " <td colspan=\"4\" class=\"title\">{0}</td>\n" +
                "</tr>\n" +
                " <tr>\n" +
                "  <td class=\"row-left\"><a href=\"{1}\">{2}</a></td>\n" +
                "  <td class=\"row-center\"><a href=\"{3}\" " + REL_EXTERNAL + ">{4}</a></td>\n" +
                "  <td class=\"row-center\"><a href=\"{5}\" " + REL_EXTERNAL + ">{6}</a></td>\n" +
                "  <td class=\"row-right\"><a href=\"{7}\">{8}</a></td>\n" +
                " </tr>\n" +
                "</table>\n" +
                "<br>\n" +
                "\n";

        SERVER_HEADER_SECTION =
                "<table class=\"data-table\">\n" +
                "<tr>\n" +
                " <td colspan=\"8\" class=\"title\">{0}</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                " <td class=\"header-center\"><small>{1}</small></td>\n" +
                " <td class=\"header-center\"><small>{2}</small></td>\n" +
                " <td class=\"header-center\"><small>{3}</small></td>\n" +
                " <td class=\"header-center\"><small>{4}</small></td>\n" +
                " <td class=\"header-center\"><small>{5}</small></td>\n" +
                " <td class=\"header-center\"><small>{6}</small></td>\n" +
                " <td class=\"header-center\"><small>{7}</small></td>\n" +
                " <td class=\"header-center\"><small>{8}</small></td>\n" +
                "</tr>\n";

        SERVER_ROW_SECTION =
                "<tr>\n" +
                " <td class=\"row-center\"><small>{0}</small></td>\n" +
                " <td class=\"row-center\"><small>{1}</small></td>\n" +
                " <td class=\"row-center\"><small>{2}</small></td>\n" +
                " <td class=\"row-center\"><small>{3}</small></td>\n" +
                " <td class=\"row-center\"><small>{4}</small></td>\n" +
                " <td class=\"row-center\"><small>{5}</small></td>\n" +
                " <td class=\"row-center\"><small>{6}</small></td>\n" +
                " <td class=\"row-center\"><small>{7}</small></td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "<br>\n" +
                "\n";

        HTML_TAIL_SECTION =
                "<hr class=\"line\">\n" +
                "<footer class=\"copyright\">\n" +
                " <em>" + HTML_COPYRIGHT_NOTICE + "</em>\n" +
                "</footer>\n" +
                "\n" +
                "</body>\n" +
                "</html>";
        //@formatter:on
    }

    /**
     * The character set used by the manager application.
     */
    public static final String CHARSET = "utf-8";

    /**
     * The XML declaration used in manager responses.
     */
    public static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"" + CHARSET + "\"?>";

    /**
     * The XML stylesheet processing instruction used in manager responses.
     */
    public static final String XML_STYLE = "<?xml-stylesheet type=\"text/xsl\" href=\"{0}/xform.xsl\" ?>\n";

    /**
     * Obtain the language tag for the {@code lang} attribute of a manager page. This is the language of the resource
     * bundle the given StringManager actually uses, which may differ from the language the client asked for if no
     * translation is available for the latter.
     *
     * @param smClient the StringManager used to render the page
     *
     * @return the IETF BCP 47 language tag of the page, or {@code en} if it is not known
     */
    public static String getLanguageTag(StringManager smClient) {
        Locale locale = smClient.getLocale();
        if (locale == null) {
            return "en";
        }
        return locale.toLanguageTag();
    }
}
