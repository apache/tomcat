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
<%@ page session="false" trimDirectiveWhitespaces="true" %>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
 <head>
  <title>Logged Out - Tomcat Host Manager Application</title>
  <style type="text/css">
    <!--
    BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;font-size:12px;}
    H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;}
    P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}
    A {color : black;}A.name {color : black;}
    HR {color : #525D76;}
    -->
  </style>
  <link href="<%=contextPath%>/images/favicon.ico" rel="icon" type="image/x-icon" />
 </head>
 <body>
   <h1>Logged Out</h1>
   <p>
    You have been successfully logged out.
   </p>
   <p id="login-link" style="display:none;">
    <a href="<%=contextPath%>/html">Click here to log in again</a>.
   </p>
   <hr size="1" noshade="noshade" />
   <p id="status" style="font-size:10px;color:#525D76;">Clearing session...</p>

    <script>
    (function() {
        // Poison the browser's credential cache to enable re-login
        function poisonCache() {
            return new Promise(function(resolve) {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', '<%=contextPath%>/html', true, 'logout', 'logout');
                xhr.onreadystatechange = function() {
                    if (xhr.readyState === 4) {
                        resolve();
                    }
                };
                xhr.onerror = function() {
                    resolve();
                };
                xhr.send();
            });
        }

        // Clear credentials
        var attempts = 0;
        var maxAttempts = 3;

        function tryPoison() {
            attempts++;
            poisonCache().then(function() {
                if (attempts < maxAttempts) {
                    setTimeout(tryPoison, 100);
                } else {
                    document.getElementById('status').style.display = 'none';
                    document.getElementById('login-link').style.display = 'block';
                }
            });
        }

        tryPoison();
    })();
    </script>
 </body>
</html>
