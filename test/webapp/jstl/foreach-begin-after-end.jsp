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
<%@ page session="false" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" import="java.util.Iterator,java.util.List" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    Iterator<String> iterator = Arrays.asList("a", "b", "c", "d").iterator();
    pageContext.setAttribute("values", iterator);
    pageContext.setAttribute("array", new String[] { "a", "b", "c", "d" });
%>
<c:forEach items="${['a','b','c','d']}" begin="2" end="1" var="i">
    <p>FAIL - list body executed</p>
</c:forEach>
<c:forEach items="${array}" begin="2" end="1" var="i">
    <p>FAIL - array body executed</p>
</c:forEach>
<c:forEach items="${values}" begin="2" end="1" var="i">
    <p>FAIL - iterator body executed</p>
</c:forEach>
<p>iteratorNext=<%= iterator.next() %></p>
<c:forEach items="${['a','b','c']}" begin="1" end="1" var="i">
    <p>equal=${i}</p>
</c:forEach>
<c:forEach items="${['a','b','c']}" end="0" var="i">
    <p>zero=${i}</p>
</c:forEach>
