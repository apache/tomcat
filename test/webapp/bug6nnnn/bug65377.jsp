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
<%@ taglib uri="http://tomcat.apache.org/tag-setters" prefix="ts" %>
<html>
  <head><title>Bug 65377 test case</title></head>
  <body>
  <p>boolean01:<ts:tagPrimitiveBoolean foo="true" />:<ts:tagBoolean foo="true" /></p>
  <p>boolean02:<ts:tagPrimitiveBoolean foo="false" />:<ts:tagBoolean foo="false" /></p>
  <p>boolean03:<ts:tagPrimitiveBoolean foo="" />:<ts:tagBoolean foo="" /></p>
  <p>byte01:<ts:tagPrimitiveByte foo="12" />:<ts:tagByte foo="12" /></p>
  <p>byte02:<ts:tagPrimitiveByte foo="" />:<ts:tagByte foo="" /></p>
  <p>short01:<ts:tagPrimitiveShort foo="1234" />:<ts:tagShort foo="1234" /></p>
  <p>short02:<ts:tagPrimitiveShort foo="" />:<ts:tagShort foo="" /></p>
  <p>character01:<ts:tagPrimitiveCharacter foo="bar" />:<ts:tagCharacter foo="bar" /></p>
  <p>character02:<ts:tagPrimitiveCharacter foo="" />:<ts:tagCharacter foo="" /></p>
  <p>integer01:<ts:tagPrimitiveInteger foo="1234" />:<ts:tagInteger foo="1234" /></p>
  <p>integer02:<ts:tagPrimitiveInteger foo="" />:<ts:tagInteger foo="" /></p>
  <p>long01:<ts:tagPrimitiveLong foo="1234" />:<ts:tagLong foo="1234" /></p>
  <p>long02:<ts:tagPrimitiveLong foo="" />:<ts:tagLong foo="" /></p>
  <p>float01:<ts:tagPrimitiveFloat foo="12.34" />:<ts:tagFloat foo="12.34" /></p>
  <p>float02:<ts:tagPrimitiveFloat foo="" />:<ts:tagFloat foo="" /></p>
  <p>double01:<ts:tagPrimitiveDouble foo="12.34" />:<ts:tagDouble foo="12.34" /></p>
  <p>double02:<ts:tagPrimitiveDouble foo="" />:<ts:tagDouble foo="" /></p>
  </body>
</html>