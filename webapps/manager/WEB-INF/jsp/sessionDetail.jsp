<?xml version="1.0" encoding="ISO-8859-1"?>
<%--
 Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--%>
<%@page session="false" contentType="text/html; charset=ISO-8859-1" %>
<%@page import="java.util.Enumeration" %>
<%@page import="javax.servlet.http.HttpSession" %>
<%@page import="org.apache.catalina.Session" %>
<%@page import="org.apache.catalina.manager.JspHelper" %>
<!DOCTYPE html 
     PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<%--!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
 "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"--%>

<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
<% String path = (String) request.getAttribute("path");
   Session currentSession = (Session)request.getAttribute("currentSession");
   HttpSession currentHttpSession = currentSession.getSession();
   String currentSessionId = currentSession.getId();
   String submitUrl = response.encodeURL(((HttpServletRequest)
           pageContext.getRequest()).getRequestURL().toString());
%>
<head>
    <meta http-equiv="content-type" content="text/html; charset=iso-8859-1"/>
    <meta http-equiv="pragma" content="no-cache"/><!-- HTTP 1.0 -->
    <meta http-equiv="cache-control" content="no-cache,must-revalidate"/><!-- HTTP 1.1 -->
    <meta http-equiv="expires" content="0"/><!-- 0 is an invalid value and should be treated as 'now' -->
    <meta http-equiv="content-language" content="en"/>
    <meta name="author" content="Cedrik LIME"/>
    <meta name="copyright" content="copyright 2005-2010 the Apache Software Foundation"/>
    <meta name="robots" content="noindex,nofollow,noarchive"/>
    <title>Sessions Administration: details for <%= currentSessionId %></title>
</head>
<body>
<h1>Details for Session <%= JspHelper.escapeXml(currentSessionId) %></h1>

<table style="text-align: left;" border="0">
  <tr>
    <th>Session Id</th>
    <td><%= currentSessionId %></td>
  </tr>
  <tr>
    <th>Guessed Locale</th>
    <td><%= JspHelper.guessDisplayLocaleFromSession(currentSession) %></td>
  </tr>
  <tr>
    <th>Guessed User</th>
    <td><%= JspHelper.guessDisplayUserFromSession(currentSession) %></td>
  </tr>
  <tr>
    <th>Creation Time</th>
    <td><%= JspHelper.getDisplayCreationTimeForSession(currentSession) %></td>
  </tr>
  <tr>
    <th>Last Accessed Time</th>
    <td><%= JspHelper.getDisplayLastAccessedTimeForSession(currentSession) %></td>
  </tr>
  <tr>
    <th>Session Max Inactive Interval</th>
    <td><%= JspHelper.secondsToTimeString(currentSession.getMaxInactiveInterval()) %></td>
  </tr>
  <tr>
    <th>Used Time</th>
    <td><%= JspHelper.getDisplayUsedTimeForSession(currentSession) %></td>
  </tr>
  <tr>
    <th>Inactive Time</th>
    <td><%= JspHelper.getDisplayInactiveTimeForSession(currentSession) %></td>
  </tr>
  <tr>
    <th>TTL</th>
    <td><%= JspHelper.getDisplayTTLForSession(currentSession) %></td>
  </tr>
</table>

<form method="post" action="<%= submitUrl %>">
  <div>
    <input type="hidden" name="path" value="<%= path %>" />
    <input type="hidden" name="sessionId" value="<%= currentSessionId %>" />
    <input type="hidden" name="action" value="sessionDetail" />
    <input type="submit" value="Refresh" />
  </div>
</form>

<div class="error"><%= JspHelper.escapeXml(request.getAttribute("error")) %></div>
<div class="message"><%= JspHelper.escapeXml(request.getAttribute("message")) %></div>

<table style="text-align: left;" border="1" cellpadding="2" cellspacing="2">
<% int nAttributes = 0;
   Enumeration attributeNamesEnumeration = currentHttpSession.getAttributeNames();
   while (attributeNamesEnumeration.hasMoreElements()) {
       attributeNamesEnumeration.nextElement();
       ++nAttributes;
   }
%>
    <caption style="font-variant: small-caps;"><%= JspHelper.formatNumber(nAttributes) %> attributes</caption>
    <thead>
        <tr>
            <th>Remove Attribute</th>
            <th>Attribute name</th>
            <th>Attribute value</th>
        </tr>
    </thead>
    <%--tfoot>
        <tr>
            <td colspan="3" style="text-align: center;">
                TODO: set Max Inactive Interval on sessions
            </td>
        </tr>
    </tfoot--%>
    <tbody>
<% attributeNamesEnumeration = currentHttpSession.getAttributeNames();
   while (attributeNamesEnumeration.hasMoreElements()) {
       String attributeName = (String) attributeNamesEnumeration.nextElement();
%>
        <tr>
            <td align="center">
                <form method="post" action="<%= submitUrl %>">
                    <div>
                        <input type="hidden" name="path" value="<%= path %>" />
                        <input type="hidden" name="action" value="removeSessionAttribute" />
                        <input type="hidden" name="sessionId" value="<%= currentSessionId %>" />
                        <input type="hidden" name="attributeName" value="<%= attributeName %>" />
                        <%
                          if ("Primary".equals(request.getAttribute("sessionType"))) {
                        %>
                          <input type="submit" value="Remove" />
                        <%
                          } else {
                            out.print("Primary sessions only");
                          }
                        %>
                    </div>
                </form>
            </td>
            <td><%= JspHelper.escapeXml(attributeName) %></td>
            <td><% Object attributeValue = currentHttpSession.getAttribute(attributeName); %><span title="<%= attributeValue == null ? "" : attributeValue.getClass().toString() %>"><%= JspHelper.escapeXml(attributeValue) %></span></td>
        </tr>
<% } // end while %>
    </tbody>
</table>

<form method="post" action="<%=submitUrl%>">
  <p style="text-align: center;">
    <input type="hidden" name="path" value="<%= path %>" />
    <input type="submit" value="Return to session list" />
  </p>
</form>

<%--div style="display: none;">
<p>
    <a href="http://validator.w3.org/check?uri=referer"><img
        src="http://www.w3.org/Icons/valid-html401"
        alt="Valid HTML 4.01!" height="31" width="88"></a>
    <a href="http://validator.w3.org/check?uri=referer"><img
        src="http://www.w3.org/Icons/valid-xhtml10"
        alt="Valid XHTML 1.0!" height="31" width="88" /></a>
    <a href="http://validator.w3.org/check?uri=referer"><img
        src="http://www.w3.org/Icons/valid-xhtml11"
        alt="Valid XHTML 1.1!" height="31" width="88" /></a>
</p>
</div--%>

</body>
</html>
