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
<c:set var="d" value="#{1+1}" />
<p>DEFERRED=[${d}]</p>
<% out.print("ATTR=[" + pageContext.getAttribute("d") + "]"); %>
<c:set var="d5" value="#{2}" scope="page" />
<p>EXPLICIT=[${d5}]</p>
<c:set var="d6" value="#{1}" />
<c:set var="d6" value="#{3}" />
<p>REPLACE=[${d6}]</p>
<c:set var="d2" value="#{'lazy'}" />
<p>FIRST=[${d2}]</p>
<c:set var="d2" value="plain" />
<p>SECOND=[${d2}]</p>
<c:set var="d3" value="#{'gone'}" />
<c:set var="d3" value="${null}" />
<p>THIRD=[${d3}]</p>
<c:catch var="err"><c:set var="d4" value="#{1+1}" scope="request" /></c:catch>
<p>ERR=[${err ne null ? 'threw' : 'silent'}]</p>
<p>ERRMSG=[${err.message}]</p>
<c:catch var="err2"><c:set var="d7" value="#{1+1}" scope="session" /></c:catch>
<p>ERR2=[${err2 ne null ? 'threw' : 'silent'}]</p>
