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
         pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:url var="u" value="/a.jsp"><c:forEach var="i" varStatus="s"
    begin="1" end="1"><c:param name="p" value="q"/></c:forEach></c:url>
<p>U=[${u}]</p>
<c:url var="mixed" value="/a.jsp?q=1">
  <c:param name="a" value="1"/>
  <c:forEach var="i" varStatus="s" begin="1" end="1"><c:param name="b" value="2"/></c:forEach>
  <c:param name="c" value="3"/>
</c:url>
<p>MIXED=[${mixed}]</p>
<c:url var="outer" value="/o.jsp">
  <c:param name="x" value="1"/>
  <c:url var="inner" value="/i.jsp">
    <c:forEach var="i" varStatus="s" begin="1" end="1"><c:param name="y" value="2"/></c:forEach>
  </c:url>
  <c:param name="z" value="3"/>
</c:url>
<p>OUTER=[${outer}]</p>
<p>INNER=[${inner}]</p>
<c:url var="ordered" value="/a.jsp?q=1&r=2">
  <c:param name="a" value="1"/>
  <c:param name="b" value="2"/>
</c:url>
<p>ORDERED=[${ordered}]</p>
