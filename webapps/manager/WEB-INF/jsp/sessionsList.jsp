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
<%@page session="false" contentType="text/html; charset=UTF-8" %>
<%@page import="java.io.IOException" %>
<%@page import="java.util.Collection" %>
<%@page import="org.apache.catalina.Session" %>
<%@page import="org.apache.catalina.manager.Constants" %>
<%@page import="org.apache.catalina.manager.JspHelper" %>
<%@page import="org.apache.catalina.util.ContextName" %>
<%@page import="java.lang.reflect.Method"%>
<%@page import="org.apache.catalina.manager.DummyProxySession"%>
<%!
private static final Class<?> deltaSessionClass;
private static final Method isPrimaryMethod;
static {
    Class<?> clazz = null;
    Method method = null;
    try {
        clazz = Class.forName("org.apache.catalina.ha.session.DeltaSession");
        method = clazz.getMethod("isPrimarySession");
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
        // Expected when either or both clustering JARs are not present
    } catch (NoSuchMethodException e) {
        // Should not happen if the class is available
    }
    deltaSessionClass = clazz;
    isPrimaryMethod = method;
}

/*
 * The columns of the sessions table: the key submitted by the column's sort
 * button and the column label (HTML). There is no comparator for the session
 * type so the Type column sorts by id.
 */
private static final String[][] COLUMNS = {
        { "id", "Session Id" },
        { "id", "Type" },
        { "locale", "Guessed Locale" },
        { "user", "Guessed User name" },
        { "CreationTime", "Creation Time" },
        { "LastAccessedTime", "Last Accessed Time" },
        { "UsedTime", "Used Time" },
        { "InactiveTime", "Inactive Time" },
        { "TTL", "<span title=\"Time To Live\">TTL</span>" } };

/*
 * Writes the column header cells for <thead> and <tfoot>. If ariaSort is not
 * null, it is the aria-sort value of the first column that sorts by sortKey.
 * Only that column is marked because Type sorts by id as well.
 */
private static void printColumnHeaders(JspWriter out, String sortKey, String ariaSort) throws IOException {
    boolean marked = false;
    for (String[] column : COLUMNS) {
        out.print("\n                    <th scope=\"col\"");
        if (ariaSort != null && !marked && column[0].equalsIgnoreCase(sortKey)) {
            out.print(" aria-sort=\"" + ariaSort + "\"");
            marked = true;
        }
        out.print("><button type=\"submit\" class=\"sort\" name=\"sort\" value=\"" + column[0] + "\">" +
                column[1] + "</button></th>");
    }
}
%>
<!DOCTYPE html>
<html lang="en">
<% String path = (String) request.getAttribute("path");
   String version = (String) request.getAttribute("version");
   ContextName cn = new ContextName(path, version);
   String submitUrl = JspHelper.escapeXml(response.encodeURL(
           ((HttpServletRequest) pageContext.getRequest()).getRequestURI() +
           "?path=" + path + "&version=" + version));
   Collection<Session> activeSessions = (Collection<Session>) request.getAttribute("activeSessions");
%>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="copyright" content="<%= Constants.COPYRIGHT_NOTICE %>"/>
    <meta name="robots" content="noindex,nofollow,noarchive"/>
    <title>Sessions Administration for <%= JspHelper.escapeXml(cn.getDisplayName()) %></title>
    <link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/manager.css">
</head>
<body>
<h1>Sessions Administration for <%= JspHelper.escapeXml(cn.getDisplayName()) %></h1>

<p>Tips:</p>
<ul>
    <li>Click on a column to sort.</li>
    <li>To view a session details and/or remove a session attributes, click on its id.</li>
</ul>

<div class="error"><%= JspHelper.escapeXml(request.getAttribute("error")) %></div>
<div class="message"><%= JspHelper.escapeXml(request.getAttribute("message")) %></div>

<form action="<%= submitUrl %>" method="POST" id="sessionsForm">
    <fieldset><legend>Active HttpSessions information</legend>
        <% String order = (String) request.getAttribute("order");
           if (order == null || "".equals(order)) {
               order = "ASC";
           }
        %>
        <input type="hidden" name="order" id="sessionsFormSortOrder" value="<%= JspHelper.escapeXml(order) %>"/>
        <input type="submit" name="refresh" value="Refresh Sessions list"/>
        <%= JspHelper.formatNumber(activeSessions.size()) %> active Sessions<br/>
        <% String sort = (String) request.getAttribute("sort");
           // The order attribute holds the direction of the next sort request, the
           // opposite of the current one. It is only set if the list was sorted by
           // a valid key. The error attribute is set if sorting failed.
           Object nextOrder = request.getAttribute("order");
           String ariaSort = null;
           if (nextOrder != null && request.getAttribute("error") == null) {
               ariaSort = "DESC".equals(nextOrder) ? "ascending" : "descending";
           }
        %>
        <table class="data-table sessions">
            <thead>
                <tr><% printColumnHeaders(out, sort, ariaSort); %>
                </tr>
            </thead>
            <tbody>
<%
    for (Session currentSession : activeSessions) {
       String currentSessionId = JspHelper.escapeXml(currentSession.getId());
       String type;
       if (deltaSessionClass != null && deltaSessionClass.isInstance(currentSession)) {
           try {
               if (Boolean.TRUE.equals(isPrimaryMethod.invoke(currentSession))) {
                   type = "Primary";
               } else {
                   type = "Backup";
               }
           } catch (Exception e) {
               type = "Primary";
           }
       } else if (currentSession instanceof DummyProxySession) {
           type = "Proxy";
       } else {
           type = "Primary";
       }
%>
                <tr>
                    <td><input type="checkbox" name="sessionIds" value="<%= currentSessionId %>" />
                      <%
                        if ("Proxy".equals(type)) {
                            out.print(currentSessionId);
                        } else {
                      %>
                      <a href="<%= submitUrl %>&amp;action=sessionDetail&amp;sessionId=<%= currentSessionId %>&amp;sessionType=<%= type %>"><%= currentSessionId %></a>
                      <%
                        }
                      %>
                    </td>
                    <td><%= type %></td>
                    <td><%= JspHelper.guessDisplayLocaleFromSession(currentSession) %></td>
                    <td><%= JspHelper.guessDisplayUserFromSession(currentSession) %></td>
                    <td><%= JspHelper.getDisplayCreationTimeForSession(currentSession) %></td>
                    <td><%= JspHelper.getDisplayLastAccessedTimeForSession(currentSession) %></td>
                    <td><%= JspHelper.getDisplayUsedTimeForSession(currentSession) %></td>
                    <td><%= JspHelper.getDisplayInactiveTimeForSession(currentSession) %></td>
                    <td><%= JspHelper.getDisplayTTLForSession(currentSession) %></td>
                </tr>
<% } // end while %>
            </tbody>
            <% if (activeSessions.size() > 10) { %>
            <tfoot><%-- The same as <thead> but without aria-sort --%>
                <tr><% printColumnHeaders(out, sort, null); %>
                </tr>
            </tfoot>
            <% } // end if %>
        </table>
        <%-- The current sort key, so that Refresh and Invalidate keep the sort
             column. It must come after all the sort buttons: browsers submit the
             fields in tree order and the servlet uses the first sort value, so a
             clicked column header button takes precedence over this field. --%>
        <% if (sort != null && !sort.isEmpty()) { %>
        <input type="hidden" name="sort" value="<%= JspHelper.escapeXml(sort) %>"/>
        <% } %>
        <p class="center-text">
            <button type="submit" name="action" value="invalidateSessions">Invalidate selected Sessions</button>
        </p>
    </fieldset>
</form>

<form method="GET" action="<%=request.getContextPath()%>/html">
  <p class="center-text">
    <input type="submit" value="Return to main page" />
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
