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
<!DOCTYPE html>
<%@ page session="false" %>
<%
java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy");
request.setAttribute("year", sdf.format(new java.util.Date()));
request.setAttribute("tomcat7Url", "http://tomcat.apache.org/");
request.setAttribute("tomcat7DocUrl", "/docs/");
request.setAttribute("tomcat7ExamplesUrl", "/examples/");
%>
<html lang="en">
    <head>
        <title><%=request.getServletContext().getServerInfo() %></title>
        <link href="favicon.ico" rel="icon" type="image/x-icon" />
        <link href="favicon.ico" rel="shortcut icon" type="image/x-icon" />
        <link href="tomcat.css" rel="stylesheet" type="text/css" />
    </head>

    <body>
        <div id="wrapper">
            <div id="navigation" class="curved container">
                <span id="nav-home"><a href="${tomcat7Url}">Home</a></span>
                <span id="nav-hosts"><a href="${tomcat7DocUrl}">Documentation</a></span>
                <span id="nav-config"><a href="${tomcat7DocUrl}config/">Configuration</a></span>
                <span id="nav-examples"><a href="${tomcat7ExamplesUrl}">Examples</a></span>
                <span id="nav-wiki"><a href="http://wiki.apache.org/tomcat/FrontPage">Wiki</a></span>
                <span id="nav-lists"><a href="${tomcat7Url}lists.html">Mailing Lists</a></span>
                <span id="nav-help"><a href="${tomcat7Url}findhelp.html">Find Help</a></span>
                <br class="separator" />
            </div>
            <div id="asf-box">
                <h1>${pageContext.servletContext.serverInfo}</h1>
            </div>
            <div id="upper" class="curved container">
                <div id="congrats" class="curved container">
                    <h2>If you're seeing this, you've successfully installed Tomcat. Congratulations!</h2>
                </div>
                <div id="notice">
                    <img src="tomcat.png" alt="[tomcat logo]" />
                    <div id="tasks">
                        <h3>Recommended Reading:</h3>
                        <h4><a href="${tomcat7DocUrl}security-howto.html">Security Considerations HOW-TO</a></h4>
                        <h4><a href="${tomcat7DocUrl}manager-howto.html">Manager Application HOW-TO</a></h4>
                        <h4><a href="${tomcat7DocUrl}cluster-howto.html">Clustering/Session Replication HOW-TO</a></h4>
                    </div>
                </div>
                <div id="actions">
                    <div class="button">
                        <a class="container shadow" href="/manager/status"><span>Server Status</span></a>
                    </div>
                    <div class="button">
                        <a class="container shadow" href="/manager/html"><span>Manager App</span></a>
                    </div>
                    <div class="button">
                        <a class="container shadow" href="/host-manager/html"><span>Host Manager</span></a>
                    </div>
                </div>
                <!--
                <br class="separator" />
                -->
                <br class="separator" />
            </div>
            <div id="middle" class="curved container">
                <h3>Developer Quick Start</h3>
                <div class="col25">
                    <div class="container">
                        <p><a href="${tomcat7DocUrl}setup.html">Tomcat Setup</a></p>
                        <p><a href="${tomcat7DocUrl}appdev/">First Web Application</a></p>
                    </div>
                </div>
                <div class="col25">
                    <div class="container">
                        <p><a href="${tomcat7DocUrl}realm-howto.html">Realms &amp; AAA</a></p>
                        <p><a href="${tomcat7DocUrl}jndi-datasource-examples-howto.html">JDBC DataSources</a></p>
                    </div>
                </div>
                <div class="col25">
                    <div class="container">
                        <p><a href="${tomcat7ExamplesUrl}">Examples</a></p>
                    </div>
                </div>
                <div class="col25">
                    <div class="container">
                        <p><a href="http://wiki.apache.org/tomcat/Specifications">Servlet Specifications</a></p>
                        <p><a href="http://wiki.apache.org/tomcat/TomcatVersions">Tomcat Versions</a></p>
                    </div>
                </div>
                <br class="separator" />
            </div>
            <div id="lower">
                <div id="low-manage" class="">
                    <div class="curved container">
                        <h3>Managing Tomcat</h3>
                        <p>For security, access to the <a href="/manager/html">manager webapp</a> is restricted.
                        Users are defined in:</p>
                        <pre>$CATALINA_HOME/conf/tomcat-users.xml</pre>
                        <p>In Tomcat 7.0 access to the manager application is split between
                           different users. &nbsp; <a href="${tomcat7DocUrl}manager-howto.html">Read more...</a></p>
                        <br />
                        <h4><a href="${tomcat7DocUrl}RELEASE-NOTES.txt">Release Notes</a></h4>
                        <h4><a href="${tomcat7DocUrl}changelog.html">Changelog</a></h4>
                        <h4><a href="${tomcat7Url}migration.html">Migration Guide</a></h4>
                        <h4><a href="${tomcat7Url}security.html">Security Notices</a></h4>
                    </div>
                </div>
                <div id="low-docs" class="">
                    <div class="curved container">
                        <h3>Documentation</h3>
                        <h4><a href="${tomcat7DocUrl}">Tomcat 7.0 Documentation</a></h4>
                        <h4><a href="${tomcat7DocUrl}config/">Tomcat 7.0 Configuration</a></h4>
                        <h4><a href="http://wiki.apache.org/tomcat/FrontPage">Tomcat Wiki</a></h4>
                        <p>Find additional important configuration information in:</p>
                        <pre>$CATALINA_HOME/RUNNING.txt</pre>
                        <p>Developers may be interested in:</p>
                        <ul>
                            <li><a href="http://tomcat.apache.org/bugreport.html">Tomcat 7.0 Bug Database</a></li>
                            <li><a href="${tomcat7DocUrl}api/index.html">Tomcat 7.0 JavaDocs</a></li>
                            <li><a href="http://svn.apache.org/repos/asf/tomcat/tc7.0.x/">Tomcat 7.0 SVN Repository</a></li>
                        </ul>
                    </div>
                </div>
                <div id="low-help" class="">
                    <div class="curved container">
                        <h3>Getting Help</h3>
                        <h4><a href="${tomcat7Url}faq/">FAQ</a> and <a href="${tomcat7Url}lists.html">Mailing Lists</a></h4>
                        <p>The following mailing lists are available:</p>
                        <ul>
                            <li id="list-announce"><strong><a href="mailto:announce-subscribe@tomcat.apache.org">announce@tomcat.apache.org</a><br />
                                Important announcements, releases, security vulnerability notifications. (Low volume).</strong>
                            </li>
                            <li><a href="mailto:users-subscribe@tomcat.apache.org">users@tomcat.apache.org</a><br />
                                User support and discussion
                            </li>
                            <li><a href="mailto:taglibs-user-subscribe@tomcat.apache.org">taglibs-user@tomcat.apache.org</a><br />
                                User support and discussion for <a href="${tomcat7Url}taglibs/">Apache Taglibs</a>
                            </li>
                            <li><a href="mailto:dev-subscribe@tomcat.apache.org">dev@tomcat.apache.org</a><br />
                                Development mailing list, including commit messages
                            </li>
                        </ul>
                    </div>
                </div>
                <br class="separator" />
            </div>
            <div id="footer" class="curved container">
                <div class="col20">
                    <div class="container">
                        <h4>Other Downloads</h4>
                        <ul>
                            <li><a href="${tomcat7Url}download-connectors.cgi">Tomcat Connectors</a></li>
                            <li><a href="${tomcat7Url}download-native.cgi">Tomcat Native</a></li>
                            <li><a href="${tomcat7Url}taglibs/">Taglibs</a></li>
                            <li><a href="${tomcat7DocUrl}deployer-howto.html">Deployer</a></li>
                        </ul>
                    </div>
                </div>
                <div class="col20">
                    <div class="container">
                        <h4>Other Documentation</h4>
                        <ul>
                            <li><a href="${tomcat7Url}connectors-doc/">Tomcat Connectors</a></li>
                            <li><a href="${tomcat7Url}connectors-doc/">mod_jk Documentation</a></li>
                            <li><a href="${tomcat7Url}native-doc/">Tomcat Native</a></li>
                            <li><a href="${tomcat7DocUrl}deployer-howto.html">Deployer</a></li>
                        </ul>
                    </div>
                </div>
                <div class="col20">
                    <div class="container">
                        <h4>Get Involved</h4>
                        <ul>
                            <li><a href="${tomcat7Url}getinvolved.html">Overview</a></li>
                            <li><a href="${tomcat7Url}svn.html">SVN Repositories</a></li>
                            <li><a href="${tomcat7Url}lists.html">Mailing Lists</a></li>
                            <li><a href="http://wiki.apache.org/tomcat/FrontPage">Wiki</a></li>
                        </ul>
                    </div>
                </div>
                <div class="col20">
                    <div class="container">
                        <h4>Miscellaneous</h4>
                        <ul>
                            <li><a href="${tomcat7Url}contact.html">Contact</a></li>
                            <li><a href="${tomcat7Url}legal.html">Legal</a></li>
                            <li><a href="http://www.apache.org/foundation/sponsorship.html">Sponsorship</a></li>
                            <li><a href="http://www.apache.org/foundation/thanks.html">Thanks</a></li>
                        </ul>
                    </div>
                </div>
                <div class="col20">
                    <div class="container">
                        <h4>Apache Software Foundation</h4>
                        <ul>
                            <li><a href="${tomcat7Url}whoweare.html">Who We Are</a></li>
                            <li><a href="${tomcat7Url}heritage.html">Heritage</a></li>
                            <li><a href="http://www.apache.org">Apache Home</a></li>
                            <li><a href="${tomcat7Url}resources.html">Resources</a></li>
                        </ul>
                    </div>
                </div>
                <br class="separator" />
            </div>
            <p class="copyright">Copyright &copy;1999-${year} Apache Software Foundation.  All Rights Reserved</p>
        </div>
    </body>

</html>
