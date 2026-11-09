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
<%@ page session="false" contentType="text/html; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<!DOCTYPE html>
<html lang="en">
 <head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>401 Unauthorized</title>
  <link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
  <style nonce="${requestScope['org.apache.catalina.CSP_NONCE']}">body{font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;font-size:12px}h1{color:white;background-color:#525D76;font-size:22px}pre,code{border:1px dotted #525D76}a{color:black}</style>
 </head>
 <body>
   <h1>401 Unauthorized</h1>
   <p>
    You are not authorized to view this page. If you have not changed
    any configuration files, please examine the file
    <code>conf/tomcat-users.xml</code> in your installation. That
    file must contain the credentials to let you use this webapp.
   </p>
   <p>
    For example, to add the <code>admin-gui</code> role to a user named
    <code>tomcat</code> with a password of <code>s3cret</code>, add the following to the
    config file listed above.
   </p>
<pre>
&lt;role rolename="admin-gui"/&gt;
&lt;user username="tomcat" password="s3cret" roles="admin-gui"/&gt;
</pre>
   <p>
    Note that for Tomcat 7 onwards, the roles required to use the host manager
    application were changed from the single <code>admin</code> role to the
    following two roles. You will need to assign the role(s) required for
    the functionality you wish to access.
   </p>
    <ul>
      <li><code>admin-gui</code> - allows access to the HTML GUI</li>
      <li><code>admin-script</code> - allows access to the text interface</li>
    </ul>
   <p>
    The HTML interface is protected against CSRF but the text interface is not.
    To maintain the CSRF protection:
   </p>
   <ul>
    <li>Users with the <code>admin-gui</code> role should not be granted the
       <code>admin-script</code> role.</li>
    <li>If the text interface is accessed through a browser (e.g. for testing
        since this interface is intended for tools not humans) then the browser
        must be closed afterwards to terminate the session.</li>
   </ul>
 </body>

</html>
