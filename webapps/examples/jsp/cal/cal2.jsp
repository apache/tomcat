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
  <jsp:useBean id="table" scope="session" class="cal.TableBean" />

  <%
    String time = request.getParameter ("time");
  %>

    <font size="5">Please add the following event:
      <br>
      <h3>Date <%= table.getDate() %><br>Time <%= util.HTMLFilter.filter(time) %></h3>
    </font>
    <form method="POST" action="cal1.jsp">
      <br>
      <br> <input name="date" type="hidden" value="current">
      <br> <input name="time" type="hidden" value="<%= util.HTMLFilter.filter(time) %>">
      <br> <h2> Description of the event <input name="description" type="text" size="20"> </h2>
      <br> <input type="submit" value="submit">
    </form>
  </body>
</html>

