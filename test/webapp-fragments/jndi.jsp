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
<%@page contentType="text/plain" pageEncoding="UTF-8"%><%
    String jndiName = request.getParameter("jndiName");

    javax.naming.Context initCtx = new javax.naming.InitialContext();
    javax.naming.Context envCtx = (javax.naming.Context) initCtx.lookup("java:comp/env");

    try {
        Object obj = envCtx.lookup(jndiName);
        out.println(obj.toString());
    } catch (javax.naming.NameNotFoundException e) {
        out.println("Not Found");
    } catch (javax.naming.NamingException e) {
        out.println("Naming Error");
    }
%>