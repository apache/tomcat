<!--
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
-->
<?xml version="1.0" encoding="ISO-8859-1"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
   "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
    <head>
    <title>Apache Tomcat</title>
    <style type="text/css">
    /*<![CDATA[*/
    body {
        color: #000;
        background-color: #fff;
        color: #333;
        font-family: Arial, "Times New Roman", Times, serif;
        margin: 0;
        padding: 10px;
        min-width: 700px;
    }
    img {
       border: none;
    }
    a:link, a:visited {
        color: blue;
    }
    code {
        display: block;
        font-size: 120%;
        color: #090;
        margin: 0.5em 0.5em 0;
    }
    h1, h2 {
        font-size: 110%;
        margin-top: 0;
    }
    p#footer {
        text-align: right;
        font-size: 80%;
    }
    div#header {
        min-width: 700px;
    }
    div#header h1 {
        margin: 0;
    }
    div.fl {
        float: left;
    }
    div.fr {
        float: right;
    }
    #content div.fl {
        width: 20%;
        margin-right: 2%;
    }
    div#main {
        float: left;
        width: 77%;
    }
    .clear {
        display: block;
        clear: both;
    }
    .panel {
        border: 2px solid #000;
        background-color: #FFDC75;
        padding: 0 0 20px;
        margin: 0 0 20px;
    }
    .panel h3 {
        border-bottom: 2px solid #000;
        background-color: #D2A41C;
        margin: 0 0 2px;
        padding: 4px 4px 2px;
        font: normal 110% Verdana, "Times New Roman", Times, serif;
        font-style: italic;
    }
    .panel p {
        margin: 0;
        padding: 2px 4px 0;
    }
    /*]]>*/
    </style>
</head>

<body>

<!-- Header -->
<div id="header">
    <div class="fl">
        <img src="tomcat.gif" alt="The Mighty Tomcat - MEOW!"/>
    </div>
    <div class="fl">
        <h1>Apache Tomcat <%=request.getServletContext().getServerInfo() %></h1>
    </div>
    <div class="fr">
        <img src="asf-logo-wide.gif" alt="The Apache Software Foundation"/>
    </div>
    <span class="clear"></span>
</div>

<div id="content">
    <div class="fl">
        <div class="panel">
            <h3>Administration</h3>
            <p><a href="/manager/status">Status</a></p>
            <p><a href="/manager/html">Tomcat Manager</a></p>
            <p><a href="/host-manager/html">Host Manager</a></p>
        </div>
        <div class="panel">
            <h3>Documentation</h3>
            <p><a href="RELEASE-NOTES.txt">Release Notes</a></p>
            <p><a href="/docs/changelog.html">Change Log</a></p>
            <p><a href="/docs">Tomcat Documentation</a></p>
        </div>
        <div class="panel">
            <h3>Tomcat Online</h3>
            <p><a href="http://tomcat.apache.org/">Home Page</a></p>
            <p><a href="http://tomcat.apache.org/faq/">FAQ</a></p>
            <p><a href="http://tomcat.apache.org/bugreport.html">Bug Database</a></p>
            <p><a href="http://issues.apache.org/bugzilla/buglist.cgi?bug_status=UNCONFIRMED&amp;bug_status=NEW&amp;bug_status=ASSIGNED&amp;bug_status=REOPENED&amp;bug_status=RESOLVED&amp;resolution=LATER&amp;resolution=REMIND&amp;resolution=---&amp;bugidtype=include&amp;product=Tomcat+7&amp;cmdtype=doit&amp;order=Importance">Open Bugs</a></p>
            <p><a href="http://mail-archives.apache.org/mod_mbox/tomcat-users/">Users Mailing List</a></p>
            <p><a href="http://mail-archives.apache.org/mod_mbox/tomcat-dev/">Developers Mailing List</a></p>
            <p><a href="irc://irc.freenode.net/#tomcat">IRC</a></p>
        </div>
        <div class="panel">
            <h3>Miscellaneous</h3>
            <p><a href="http://localhost:8080/examples/servlets/">Servlets Examples</a></p>
            <p><a href="http://localhost:8080/examples/jsp/">JSP Examples</a></p>
            <p><a href="http://java.sun.com/products/jsp">Sun's Java Server Pages Site</a></p>
            <p><a href="http://java.sun.com/products/servlet">Sun's Servlet Site</a></p>
        </div>
    </div>
    <div id="main">
        <h2>If you're seeing this page via a web browser, it means you've setup Tomcat successfully. Congratulations!</h2>
        <p>Now join the Tomcat Announce mailing list, which is a low volume mailing list for releases, security vulnerabilities and other project announcements.</p>
        <ul>
            <li><strong><a href="mailto:announce-subscribe@tomcat.apache.org">announce-subscribe@tomcat.apache.org</a> for important announcements.</strong></li>
        </ul>
        <p>As you may have guessed by now, this is the default Tomcat home page. It can be found on the local filesystem at: 
            <code>$CATALINA_HOME/webapps/ROOT/index.html</code></p>
        <p>where &quot;$CATALINA_HOME&quot; is the root of the Tomcat installation directory. If you're seeing this page, and you don't think you should be, then you're either a user who has arrived at new installation of Tomcat, or you're an administrator who hasn't got his/her setup quite right. Providing the latter is the case, please refer to the <a href="/docs">Tomcat Documentation</a> for more detailed setup and administration information than is found in the INSTALL file.</p>
        <p><strong>NOTE: For security reasons, using the manager webapp is restricted to users with role "manager-gui".</strong>
            Users are defined in: <code>$CATALINA_HOME/conf/tomcat-users.xml</code></p>
        <p>Included with this release are a host of sample Servlets and JSPs (with associated source code), extensive documentation, and an introductory guide to developing web applications.</p>
        <p>Tomcat mailing lists are available at the Tomcat project web site:</p>
        <ul>
            <li><strong><a href="mailto:users@tomcat.apache.org">users@tomcat.apache.org</a></strong> for general questions related to configuring and using Tomcat</li>
            <li><strong><a href="mailto:dev@tomcat.apache.org">dev@tomcat.apache.org</a></strong> for developers working on Tomcat</li>
        </ul>
        <p>Thanks for using Tomcat!</p>
        <p id="footer">
            <img src="tomcat-power.gif" width="77" height="80" alt="Powered by Tomcat"/><br/>
            &nbsp;
            Copyright &copy; 1999-@YEAR@ Apache Software Foundation<br/>
            All Rights Reserved
        </p>
    </div>
    <span class="clear"></span>
</div>
</body>
</html>
