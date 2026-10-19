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
<%@ page session="true" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<% request.setAttribute("testReader", new java.io.StringReader("READOK")); %>
<p>TRIM=[<c:out value="${sessionScope.doesNotExist}">
    spaced body
</c:out>]</p>
<p>ESC=[<c:out value="${sessionScope.doesNotExist}">${'<x>'}</c:out>]</p>
<p>RAW=[<c:out value="${sessionScope.doesNotExist}"
    escapeXml="false">${'<x>'}</c:out>]</p>
<p>SPACE=[<c:out value="${sessionScope.doesNotExist}"> </c:out>]</p>
<p>SKIP=[<c:out value="V">BODY</c:out>]</p>
<p>NONE=[<c:out value="${sessionScope.doesNotExist}"/>]</p>
<p>NEST=[<c:out value="${sessionScope.doesNotExist}">AB <c:out
    value="${sessionScope.doesNotExist}"> inner </c:out> CD</c:out>]</p>
<p>READER=[<c:out value="${testReader}">IGNORED</c:out>]</p>
<p>LEAK=[<c:catch><c:out value="${sessionScope.doesNotExist}">lost<%
    if (true) {
        throw new IllegalStateException("body failed");
    }
%></c:out></c:catch>]</p>
<p>TAIL=[visible]</p>
