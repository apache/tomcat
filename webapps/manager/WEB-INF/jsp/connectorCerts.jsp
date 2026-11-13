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
<%@page import="java.util.Map" %>
<%@page import="java.util.Map.Entry" %>
<%@page import="java.util.List" %>
<%@page import="org.apache.catalina.manager.Constants" %>
<!DOCTYPE html>
<html lang="en">
<% Map<String,List<String>> certList = (Map<String,List<String>>) request.getAttribute("certList");
%>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="copyright" content="<%= Constants.COPYRIGHT_NOTICE %>"/>
    <meta name="robots" content="noindex,nofollow,noarchive"/>
    <title>Configured certificate chains per Connector</title>
    <link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/manager.css">
</head>
<body>
<h1>Configured certificate chains per Connector</h1>

<table class="data-table table-left">
    <thead>
        <tr>
            <th>Connector / TLS Virtual Host / Certificate type</th>
            <th>Certificate chain</th>
        </tr>
    </thead>
    <tbody>
        <%
        for (Map.Entry<String, List<String>> entry : certList.entrySet()) {
        %>
        <tr>
            <td><%=entry.getKey()%></td>
            <td>
            <%
            for (String cert : entry.getValue()) {
            %>
                <pre><%=cert%></pre>
            <%
            }
            %>
            </td>
        </tr>
        <%
        }
        %>
    </tbody>
</table>

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
