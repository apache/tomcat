<html>
<head>
<title>Sample Application JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
<tr>
<td align=center>
<img src="images/tomcat.gif">
</td>
<td>
<h1>Sample Application JSP Page</h1>
This is the output of a JSP page that is part of the Hello, World
application.  It displays several useful values from the request
we are currently processing.
</td>
</tr>
</table>

<table border="0" border="100%">
<tr>
  <th align="right">Context Path:</th>
  <td align="left"><%= request.getContextPath() %></td>
</tr>
<tr>
  <th align="right">Path Information:</th>
  <td align="left"><%= request.getPathInfo() %></td>
</tr>
<tr>
  <th align="right">Query String:</th>
  <td align="left"><%= request.getQueryString() %></td>
</tr>
<tr>
  <th align="right">Request Method:</th>
  <td align="left"><%= request.getMethod() %></td>
</tr>
<tr>
  <th align="right">Servlet Path:</th>
  <td align="left"><%= request.getServletPath() %></td>
</tr>
</table>
</body>
</html>
