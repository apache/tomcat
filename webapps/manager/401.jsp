<%
  response.setHeader("WWW-Authenticate", "Basic realm=\"Tomcat Manager Application\"");
%>
<html>
 <head>
  <title>401 Unauthorized</title>
  <style>
    <!--
    BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;font-size:12px;}
    H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;}
    PRE, TT {border: 1px dotted #525D76}
    A {color : black;}A.name {color : black;}
    -->
  </style>
 </head>
 <body>
   <h1>401 Unauthorized</h1>
   <p>
    You are not authorized to view this page. If you have not changed
    any configuration files, please examine the file
    <tt>conf/tomcat-users.xml</tt> in your installation. That
    file will contain the credentials to let you use this webapp.
   </p>
   <p>
    You will need to add <tt>manager</tt> role to the config file listed above.
    For example:
<pre>
&lt;role rolename="manager"/&gt;
&lt;user username="tomcat" password="s3cret" roles="manager"/&gt;
</pre>
   </p>
   <p>
    For more information - please see the
    <a href="/docs/manager-howto.html">Manager App HOW-TO</a>.
   </p>
 </body>

</html>
