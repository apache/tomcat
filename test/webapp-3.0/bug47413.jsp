<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
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
<jsp:useBean id="values" class="jsp2.examples.ValuesBean" />
<html>
  <head><title>Bug 47413 test case</title></head>
  <body>
    <jsp:setProperty name="values" property="string" value="${'hello'} wo${'rld'}"/>
    <p>00-${values.string}</p>
    <tags:echo echo="01-${'hello'} wo${'rld'}"/>

    <jsp:setProperty name="values" property="double" value="${1+2}.${220}"/>
    <p>02-${values.double}</p>
    <tags:echo-double index="03" echo="${1+2}.${220}"/>
    
    <jsp:setProperty name="values" property="long" value="000${1}${7}"/>
    <p>04-${values.long}</p>
    <tags:echo-long index="05" echo="000${1}${7}"/>
    
    <jsp:setProperty name="values" property="string"
                     value="${undefinedFoo}hello world${undefinedBar}"/>
    <p>06-${values.string}</p>
    <tags:echo echo="${undefinedFoo}07-hello world${undefinedBar}"/>

    <jsp:setProperty name="values" property="double"
                     value="${undefinedFoo}${undefinedBar}"/>
    <p>08-${values.double}</p>
    <tags:echo-double index="09" echo="${undefinedFoo}${undefinedBar}"/>

    <jsp:setProperty name="values" property="long"
                     value="${undefinedFoo}${undefinedBar}"/>
    <p>10-${values.long}</p>
    <tags:echo-long index="11" echo="${undefinedFoo}${undefinedBar}"/>

  </body>
</html>

