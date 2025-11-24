<%@ page contentType="text/html; charset=UTF-8" %>
<html>
<head>
<link href="<%=request.getContextPath()%>/images/favicon.ico" rel="icon" type="image/x-icon" />
<link rel="stylesheet" href="<%=request.getContextPath()%>/css/manager.css">
<title>Tomcat Virtual Host Manager - Login</title>
</head>

<body bgcolor="#FFFFFF">

<table cellspacing="4" border="0">
 <tr>
  <td colspan="2">
   <a href="https://tomcat.apache.org/" rel="noopener noreferrer">
    <img class='tomcat-logo' alt="The Tomcat Servlet/JSP Container"
         src="<%=request.getContextPath()%>/images/tomcat.svg">
   </a>
   <a href="https://www.apache.org/" rel="noopener noreferrer">
    <img border="0" alt="The Apache Software Foundation" align="right"
         src="<%=request.getContextPath()%>/images/asf-logo.svg" style="width: 266px; height: 83px;">
   </a>
  </td>
 </tr>
</table>
<hr size="1" noshade="noshade">
<table cellspacing="4" border="0">
 <tr>
  <td class="page-title" bordercolor="#000000" align="left" nowrap>
   <font size="+2">Tomcat Virtual Host Manager</font>
  </td>
 </tr>
</table>
<br>

<table border="1" cellspacing="0" cellpadding="3">
 <tr>
  <td colspan="2" class="title">Login</td>
 </tr>
 <tr>
  <td colspan="2">
   <form method="POST" action="j_security_check">
    <table cellpadding="3" cellspacing="0">
     <tr>
      <td class="row-left"><strong>Username:</strong></td>
      <td class="row-left"><input type="text" name="j_username" size="20" autofocus required></td>
     </tr>
     <tr>
      <td class="row-left"><strong>Password:</strong></td>
      <td class="row-left"><input type="password" name="j_password" size="20" required></td>
     </tr>
     <tr>
      <td class="row-left">&nbsp;</td>
      <td class="row-left"><input type="submit" value="Log In"></td>
     </tr>
    </table>
   </form>
  </td>
 </tr>
</table>

<br>
<hr size="1" noshade="noshade">
<center><font size="-1" color="#525D76">
 Copyright &copy; 1999-2025, Apache Software Foundation
</font></center>

</body>
</html>
