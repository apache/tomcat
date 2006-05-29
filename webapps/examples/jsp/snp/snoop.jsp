<html>
<!--
  Copyright 2004 The Apache Software Foundation

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

<body bgcolor="white">
<h1> Request Information </h1>
<font size="4">
JSP Request Method: <% out.print(util.HTMLFilter.filter(request.getMethod())); %>
<br>
Request URI: <%= request.getRequestURI() %>
<br>
Request Protocol: <%= request.getProtocol() %>
<br>
Servlet path: <%= request.getServletPath() %>
<br>
Path info: <% out.print(util.HTMLFilter.filter(request.getPathInfo())); %>
<br>
Query string: <% out.print(util.HTMLFilter.filter(request.getQueryString())); %>
<br>
Content length: <%= request.getContentLength() %>
<br>
Content type: <% out.print(util.HTMLFilter.filter(request.getContentType())); %>
<br>
Server name: <%= request.getServerName() %>
<br>
Server port: <%= request.getServerPort() %>
<br>
Remote user: <%= request.getRemoteUser() %>
<br>
Remote address: <%= request.getRemoteAddr() %>
<br>
Remote host: <%= request.getRemoteHost() %>
<br>
Authorization scheme: <%= request.getAuthType() %> 
<br>
Locale: <%= request.getLocale() %>
<hr>
The browser you are using is <% out.print(util.HTMLFilter.filter(request.getHeader("User-Agent"))); %>
<hr>
</font>
</body>
</html>
