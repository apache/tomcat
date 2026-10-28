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
<%@ page import="org.apache.tomcat.util.security.Escape" session="false"
         trimDirectiveWhitespaces="true" %>
<!DOCTYPE html>
<html lang="en">
 <head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>404 Not found</title>
  <link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
  <link rel="stylesheet" href="<%=request.getContextPath()%>/css/manager.css">
 </head>
 <body>
   <h1>404 Not found</h1>
   <p>
    The page you tried to access
    (<%=Escape.htmlElementContent((String) request.getAttribute(
            "jakarta.servlet.error.request_uri"))%>)
    does not exist.
   </p>
   <p>
    The Manager application has been re-structured for Tomcat 7 onwards and some
    of URLs have changed. All URLs used to access the Manager application should
    now start with one of the following options:
   </p>
    <ul>
      <li><%=request.getContextPath()%>/html for the HTML GUI</li>
      <li><%=request.getContextPath()%>/text for the text interface</li>
      <li><%=request.getContextPath()%>/jmxproxy for the JMX proxy</li>
      <li><%=request.getContextPath()%>/status for the status pages</li>
    </ul>
   <p>
    Note that the URL for the text interface has changed from
    &quot;<%=request.getContextPath()%>&quot; to
    &quot;<%=request.getContextPath()%>/text&quot;.
   </p>
   <p>
    You probably need to adjust the URL you are using to access the Manager
    application. However, there is always a chance you have found a bug in the
    Manager application. If you are sure you have found a bug, and that the bug
    has not already been reported, please report it to the Apache Tomcat team.
   </p>
 </body>
</html>
