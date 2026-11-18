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
<%
    request.setAttribute("i", "outerI");
    request.setAttribute("j", "outerJ");
%>
<c:forEach items="${['a','b']}" var="i">
    <p>items=${i}</p>
</c:forEach>
<p>afterItems=${i}</p>
<c:forEach begin="1" end="2" var="j">
    <p>range=${j}</p>
</c:forEach>
<p>afterRange=${j}</p>
<c:forEach items="${['a','b']}" var="none">
    <p>none=${none}</p>
</c:forEach>
<p>afterNone=${none}</p>
