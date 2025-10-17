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
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<%@ page import="jakarta.el.TesterBeanA" %>
<%@ page import="jakarta.el.TesterBeanB" %>
<html>
  <head><title>EL method test cases</title></head>
  <body>
    <%
    TesterBeanA beanA1 = new TesterBeanA();

    TesterBeanA beanA2 = new TesterBeanA();
    TesterBeanB beanB = new TesterBeanB("test");
    beanA2.setBeanB(beanB);

    pageContext.setAttribute("testBeanA1", beanA1, PageContext.REQUEST_SCOPE);
    pageContext.setAttribute("testBeanA2", beanA2, PageContext.REQUEST_SCOPE);
    %>

    <tags:echo echo="00-${testBeanA1.beanBOpt}" />
    <tags:echo echo="01-${testBeanA1.beanBOpt.name}" />
    <tags:echo echo="02-${testBeanA1.beanBOpt.map(b -> b.name)}" />
    <tags:echo echo="03-${testBeanA1.beanBOpt.doSomething()}" />
    <tags:echo echo="10-${testBeanA2.beanBOpt}" />
    <tags:echo echo="11-${testBeanA2.beanBOpt.name}" />
    <%-- Triggers MethodNotFoundException
    <tags:echo echo="12-${testBeanA2.beanBOpt.map(b -> b.name)}" />
    --%>
    <tags:echo echo="13-${testBeanA2.beanBOpt.doSomething()}" />

    <tags:echo-deferred echo="20-#{testBeanA1.beanBOpt}" />
    <tags:echo-deferred echo="21-#{testBeanA1.beanBOpt.name}" />
    <tags:echo-deferred echo="22-#{testBeanA1.beanBOpt.map(b -> b.name)}" />
    <tags:echo-deferred echo="23-#{testBeanA1.beanBOpt.doSomething()}" />
    <tags:echo-deferred echo="30-#{testBeanA2.beanBOpt}" />
    <tags:echo-deferred echo="31-#{testBeanA2.beanBOpt.name}" />
    <%-- Triggers MethodNotFoundException
    <tags:echo-deferred echo="32-#{testBeanA2.beanBOpt.map(b -> b.name)}" />
    --%>
    <tags:echo-deferred echo="33-#{testBeanA2.beanBOpt.doSomething()}" />
  </body>
</html>