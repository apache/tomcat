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
  <title>403 Access Denied</title>
  <link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
  <style>body{font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;font-size:12px}h1{color:white;background-color:#525D76;font-size:22px}pre,code{border:1px dotted #525D76}a{color:black}</style>
 </head>
 <body>
   <h1>403 Access Denied</h1>
   <p>
    You are not authorized to view this page.
   </p>
   <p>
    By default the Host Manager is only accessible from a browser running on the
    same machine as Tomcat. If you wish to modify this restriction, you'll need
    to edit the Host Manager's <code>context.xml</code> file.
   </p>
   <p>
    If you have already configured the Host Manager application to allow access
    and you have used your browsers back button, used a saved book-mark or
    similar then you may have triggered the cross-site request forgery (CSRF)
    protection that has been enabled for the HTML interface of the Host Manager
    application. You will need to reset this protection by returning to the
    <a href="<%=request.getContextPath()%>/html">main Host Manager page</a>.
    Once you return to this page, you will be able to continue using the Host
    Manager application's HTML interface normally. If you continue to see this
    access denied message, check that you have the necessary permissions to
    access this application.
   </p>
   <p> If you have not changed
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
