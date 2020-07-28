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


public class Constants {

    public static final String Package = "org.apache.catalina.manager";

    public static final String REL_EXTERNAL = "rel=\"noopener noreferrer\"";

    public static final String HTML_HEADER_SECTION;
    public static final String BODY_HEADER_SECTION;
    public static final String MESSAGE_SECTION;
    public static final String MANAGER_SECTION;
    public static final String SERVER_HEADER_SECTION;
    public static final String SERVER_ROW_SECTION;
    public static final String HTML_TAIL_SECTION;

    static {
        HTML_HEADER_SECTION =
            "<html>\n" +
            "<head>\n" +
            "<style>\n" +
            org.apache.catalina.util.TomcatCSS.TOMCAT_CSS + "\n" +
            "  #manager-wrapper {\n" +
            "    min-width: 720px;\n" +
            "    max-width: 1000px;\n" +
            "    margin: 20px auto;\n" + 
            "    background: #fff;\n" + 
            "    padding: 20px 30px;\n" +
            "  }\n" +
            "  table {\n" +
            "    width: 100%;\n" +
            "  }\n" +
            "  td.page-title {\n" +
            "    font-size:52px; \n" +
            "    text-align: center;\n" +
            "    vertical-align: top;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    font-weight: bold;\n" +
            "    background: white;\n" +
            "    text-align: left;\n" +
            "    padding-top:20px\n" +
            "    color: black;\n" +
            "    padding-top: 24px;\n" +
            "  }\n" +
            "  td.page-title font{\n" +
            "    font-size:52px;\n" +
            "  }\n" + 
            "  #manager-section{\n" +
            "    margin-bottom:20px;\n" +
            "  }\n" +             
            "  td.title {\n" +
            "    color: #fff;\n" + 
            "    text-align: left;\n" +
            "    vertical-align: top;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    font-size:23px;\n" +
            "    font-weight: bold;\n" +
            "    background: #8F8464;\n" +
            "  }\n" +
            "  td.header-left {\n" +
            "    text-align: left;\n" +
            "    vertical-align: top;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    font-weight: bold;\n" +
            "    background: #FEEDB3;\n" +
            "  }\n" +
            "  td.header-center {\n" +
            "    text-align: center;\n" +
            "    vertical-align: top;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    font-weight: bold;\n" +
            "    background: #FEEDB3;\n" +
            "  }\n" +
            "  td.row-left {\n" +
            "    text-align: left;\n" +
            "    vertical-align: middle;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    color: black;\n" +
            "  }\n" +
            "  td.row-center {\n" +
            "    text-align: center;\n" +
            "    vertical-align: middle;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    color: black;\n" +
            "  }\n" +
            "  td.row-right {\n" +
            "    text-align: right;\n" +
            "    vertical-align: middle;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    color: black;\n" +
            "  }\n" +
            "  TH {\n" +
            "    text-align: center;\n" +
            "    vertical-align: top;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    font-weight: bold;\n" +
            "    background: #FFDC75;\n" +
            "  }\n" +
            "  TD {\n" +
            "    text-align: center;\n" +
            "    vertical-align: middle;\n" +
            "    font-family:sans-serif,Tahoma,Arial;\n" +
            "    color: black;\n" +
            "  }\n" +
            "  form {\n" +
            "    margin: 1;\n" +
            "  }\n" +
            "  form.inline {\n" +
            "    display: inline;\n" +
            "  }\n" +
            "  .action,\n" + 
            "  input[type=\"button\"].action,\n" + 
            "  input[type=\"reset\"].action,\n" + 
            "  input[type=\"submit\"].action {\n" + 
            "    font-size: 12px;\n" + 
            "    padding: 12px 19px !important;\n" +
            "    border-radius: 3px;\n" +
            "    -moz-border-radius: 3px;\n" +
            "    -webkit-border-radius: 3px;\n" +
            "    -webkit-box-shadow: 0px 1px 3px 0px rgba(0,0,0,0.12);\n" + 
            "    -moz-box-shadow: 0px 1px 3px 0px rgba(0,0,0,0.12);\n" + 
            "    box-shadow: 0px 1px 3px 0px rgba(0,0,0,0.12);\n" + 
            "    -webkit-appearance: none;\n" + 
            "    -moz-appearance:none;\n" + 
            "    text-transform:uppercase;\n" + 
            "    cursor:hand;\n" + 
            "    cursor:pointer;\n" + 
            "  }\n" +
            "  .action.small,\n" + 
            "  input[type=\"button\"].action.small,\n" + 
            "  input[type=\"reset\"].action.small,\n" + 
            "  input[type=\"submit\"].action.small {\n" + 
            "    font-size:10px !important;\n" +
            "    padding:10px 12px !important;\n" +
            "  }\n" +            
            "  .sky, \n" + 
            "  input[type=\"button\"].action.sky,\n" + 
            "  input[type=\"reset\"].action.sky,\n" + 
            "  input[type=\"submit\"].action.sky,\n" + 
            "  input[type=\"button\"].action.sky{\n"+ 
            "    color: #000;\n" +
            "    background: #e2e2e2;\n" + 
            "    border: solid 1px #cacaca;\n" +
            "  }\n" + 
            "  .stop, \n" + 
            "  input[type=\"button\"].action.stop,\n" + 
            "  input[type=\"reset\"].action.stop,\n" + 
            "  input[type=\"submit\"].action.stop,\n" + 
            "  input[type=\"button\"].action.stop{\n"+ 
            "    color: #fff;\n" +
            "    background: #ff4343;\n" + 
            "    border: solid 1px #ff3131;\n" +
            "  }\n" + 
            "  .start, \n" + 
            "  input[type=\"button\"].action.start,\n" + 
            "  input[type=\"reset\"].action.start,\n" + 
            "  input[type=\"submit\"].action.start,\n" + 
            "  input[type=\"button\"].action.start{\n"+ 
            "      color: #fff;\n" +
            "      background: #4E657B;\n" + 
            "      border: solid 1px #4E657B;\n" +
            "  }\n" + 
            "  input[type=\"text\"],\n" + 
            "  input[type=\"text\"]:hover,\n" + 
            "  input[type=\"text\"]:focus{\n" + 
            "    color:#221;\n" + 
            "    font-size:16px;\n" + 
            "    background:#f8f8f8;\n" + 
            "    line-height:1.0em;\n" + 
            "    padding:10px 12px;\n" + 
            "    border:solid 1px #ccc;\n" + 
            "    border-style:solid;\n" + 
            "    -webkit-border-radius: 3px;\n" + 
            "    -moz-border-radius: 3px;\n" + 
            "    border-radius: 3px;\n" + 
            "  }\n" + 
            "  .href-dotted{\n" + 
            "    color: #000;\n" + 
            "    text-decoration: none;\n" + 
            "    border-bottom:dotted 2px #000;\n" + 
            "  }\n" + 
            "</style>\n";

        BODY_HEADER_SECTION =
            "<title>{0}</title>\n" +
            "</head>\n" +
            "\n" +
            "<body bgcolor=\"#dfd3ae\">\n" +
            "\n" +
            "<div id=\"manager-wrapper\">\n" + 
            "<table cellspacing=\"4\" border=\"0\">\n" +
            " <tr>\n" +
            "  <td colspan=\"2\">\n" +
            "   <a href=\"https://tomcat.apache.org/\" " + REL_EXTERNAL + ">\n" +
            "    <img border=\"0\" alt=\"The Tomcat Servlet/JSP Container\"\n" +
            "         align=\"left\" src=\"{0}/images/tomcat.gif\">\n" +
            "   </a>\n" +
            "   <a href=\"https://www.apache.org/\" " + REL_EXTERNAL + ">\n" +
            "    <img border=\"0\" alt=\"The Apache Software Foundation\" align=\"right\"\n" +
            "         src=\"{0}/images/asf-logo.svg\" style=\"width: 139px;\">\n" +
            "   </a>\n" +
            "  </td>\n" +
            " </tr>\n" +
            "</table>\n" +
            "<table cellspacing=\"4\" border=\"0\">\n" +
            " <tr>\n" +
            "  <td class=\"page-title\" bordercolor=\"#000000\" " +
            "align=\"left\" nowrap>\n" +
            "   <font size=\"+2\">{1}</font>\n" +
            "  </td>\n" +
            " </tr>\n" +
            "</table>\n" +
            "\n";

        MESSAGE_SECTION =
            "<table border=\"0\" cellspacing=\"0\" cellpadding=\"3\">\n" +
            " <tr>\n" +
            "  <td class=\"row-left\" width=\"10%\">" +
            "<small><strong>{0}</strong></small>&nbsp;</td>\n" +
            "  <td class=\"row-left\"><pre>{1}</pre></td>\n" +
            " </tr>\n" +
            "</table>\n" +
            "<br>\n" +
            "\n";

        MANAGER_SECTION =
            "<table border=\"0\" cellspacing=\"0\" cellpadding=\"3\" id=\"manager-section\">\n" +
            "<tr>\n" +
            " <td colspan=\"4\" class=\"title\">{0}</td>\n" +
            "</tr>\n" +
            " <tr>\n" +
            "  <td class=\"row-left\"><a href=\"{1}\" class=\"href-dotted\">{2}</a></td>\n" +
            "  <td class=\"row-center\"><a href=\"{3}\" " + REL_EXTERNAL + " class=\"href-dotted\">{4}</a></td>\n" +
            "  <td class=\"row-center\"><a href=\"{5}\" " + REL_EXTERNAL + " class=\"href-dotted\">{6}</a></td>\n" +
            "  <td class=\"row-right\"><a href=\"{7}\" class=\"href-dotted\">{8}</a></td>\n" +
            " </tr>\n" +
            "</table>\n" +
            "<br>\n" +
            "\n";

        SERVER_HEADER_SECTION =
            "<table border=\"0\" cellspacing=\"0\" cellpadding=\"3\">\n" +
            "<tr>\n" +
            " <td colspan=\"8\" class=\"title\">{0}</td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"header-center\">{1}</td>\n" +
            " <td class=\"header-center\">{2}</td>\n" +
            " <td class=\"header-center\">{3}</td>\n" +
            " <td class=\"header-center\">{4}</td>\n" +
            " <td class=\"header-center\">{5}</td>\n" +
            " <td class=\"header-center\">{6}</td>\n" +
            " <td class=\"header-center\">{7}</td>\n" +
            " <td class=\"header-center\">{8}</td>\n" +
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
            "<center><font size=\"-1\" color=\"#525D76\">\n" +
            " <span class=\"copright\">Copyright &copy; 1999-2020, Apache Software Foundation</span>" +
            "</font></center>\n" +
            "</div>\n" + 
            "\n" +
            "</body>\n" +
            "</html>";
    }

    public static final String CHARSET="utf-8";

    public static final String XML_DECLARATION =
        "<?xml version=\"1.0\" encoding=\""+CHARSET+"\"?>";

    public static final String XML_STYLE =
        "<?xml-stylesheet type=\"text/xsl\" href=\"{0}/xform.xsl\" ?>\n";
}

