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

<jsp:include page="../echo-params.jsp?cmd=someCommand">
    <jsp:param name="param1" value="value1" />
</jsp:include>

<jsp:include page="/echo-params.jsp?cmd=someCommandAbs">
    <jsp:param name="param1" value="value1Abs" />
</jsp:include>

<jsp:include page="../echo-params.jsp">
    <jsp:param name="param2" value="value2" />
</jsp:include>

<jsp:include page="/echo-params.jsp">
    <jsp:param name="param2" value="value2Abs" />
</jsp:include>

<%--
    Verify expression support in page and param value.
 --%>
<%
    String initCommand = request.getParameter("init");
    if (initCommand != null) {
        String relativeUrl = "../echo-params.jsp?param3=" + initCommand;
        String absoluteUrl = "/echo-params.jsp?param3=" + initCommand + "Abs";
        String init_param = initCommand+"_param";
        String init_param_value_abs=initCommand+"Abs";
    %>
        <jsp:include page="<%=relativeUrl%>">
            <jsp:param name="param4" value="value4" />
            <jsp:param name="param5" value="<%=initCommand%>" />
        </jsp:include>
        <jsp:include page="<%=absoluteUrl%>">
            <jsp:param name="param4" value="value4Abs" />
            <jsp:param name="param5" value="<%=init_param_value_abs%>" />
        </jsp:include>
    <%
    }
%>
<%--
Following cases without jsp:param
--%>
<jsp:include page="../echo-params.jsp"/>
<jsp:include page="/echo-params.jsp"/>

<jsp:include page="../echo-params.jsp?param6=value6"/>

<jsp:include page="/echo-params.jsp?param6=value6Abs"/>