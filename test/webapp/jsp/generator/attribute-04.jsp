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
<%@ taglib uri="http://tomcat.apache.org/bugs" prefix="bugs" %>
<%
  String variable03 = null;
%>
<bugs:TesterDynamicTag bugs:x="foo">
  <jsp:attribute name="attribute04">aaa</jsp:attribute>
</bugs:TesterDynamicTag>
<bugs:TesterDynamicTag bugs:x="foo">
  <jsp:attribute name="attribute04">
  </jsp:attribute>
  <jsp:body>
    <jsp:useBean
        id="bean1"
        type="org.apache.jasper.runtime.TesterBean.Inner"
        beanName="bean1"
        />
  </jsp:body>
</bugs:TesterDynamicTag>
<bugs:TesterDynamicTag bugs:x="foo">
  <jsp:attribute name="attribute04">
  </jsp:attribute>
  <jsp:body>
    <jsp:include page="/index.html"/>
  </jsp:body>
</bugs:TesterDynamicTag>
<jsp:useBean id="bean2" class="org.apache.jasper.runtime.TesterBean" />
<bugs:TesterDynamicTag bugs:x="foo">
  <jsp:attribute name="attribute04">
  </jsp:attribute>
  <jsp:body>
    <jsp:setProperty name="bean2" property="intPrimitive" value="1" />
  </jsp:body>
</bugs:TesterDynamicTag>
