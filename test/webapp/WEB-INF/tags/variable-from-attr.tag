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
<%@ attribute name="varName" type="java.lang.String" required="true" rtexprvalue="false" %>
<%@ attribute name="varName2" type="java.lang.String" required="true" rtexprvalue="false" %>
<%@ variable name-from-attribute="varName" alias="data" scope="AT_END" %>
<%@ variable name-from-attribute="varName2" alias="data2" scope="AT_END" %>
<jsp:doBody/>