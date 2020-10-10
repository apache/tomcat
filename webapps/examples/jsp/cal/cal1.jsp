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
<%@page contentType="text/html; charset=UTF-8" %>
<html lang="en">
  <head>
    <title>Calendar: A JSP APPLICATION</title>
    <style>
      body {background-color: white;}
    </style>
  </head>
  <body>

  <%@ page language="java" import="cal.*" %>
  <jsp:useBean id="table" scope="session" class="cal.TableBean" />

  <%
    table.processRequest(request);
    if (table.getProcessError() == false) {
  %>

  <!-- HTML table goes here -->
  <center>
    <table width="60%" bgcolor="yellow" cellpadding="15">
      <tr>
        <td align="center"> <a href="cal1.jsp?date=prev"> prev </a>
        <td align="center"> Calendar:<%= table.getDate() %></td>
        <td align="center"> <a href="cal1.jsp?date=next"> next </a>
      </tr>
    </table>

    <!-- the main table -->
    <table width="60%" bgcolor="lightblue" border="1" cellpadding="10">
      <tr>
        <th> Time </th>
        <th> Appointment </th>
      </tr>
      <form method="POST" action="cal1.jsp">
      <%
        for(int i=0; i<table.getEntries().getRows(); i++) {
           cal.Entry entr = table.getEntries().getEntry(i);
      %>
        <tr>
          <td>
          <a href="cal2.jsp?time=<%= entr.getHour() %>">
            <%= entr.getHour() %> </a>
          </td>
          <td bgcolor="<%= entr.getColor() %>">
            <% out.print(util.HTMLFilter.filter(entr.getDescription())); %>
          </td>
        </tr>
      <%
        }
      %>
      </form>
    </table>
    <br>

    <!-- footer -->
    <table width="60%" bgcolor="yellow" cellpadding="15">
      <tr>
        <td align="center">  <% out.print(util.HTMLFilter.filter(table.getName())); %> :
          <% out.print(util.HTMLFilter.filter(table.getEmail())); %> </td>
      </tr>
    </table>
  </center>

  <%
      } else {
  %>
  <font size="5">
    You must enter your name and email address correctly.
  </font>
  <%
      }
  %>

  </body>
</html>
