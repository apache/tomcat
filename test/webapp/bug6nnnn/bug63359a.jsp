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
<jsp:useBean id="bean" class="org.apache.jasper.runtime.TesterBean" />
<html>
  <head><title>Bug 63359 test cases</title></head>
  <body>
    <jsp:setProperty name="bean" property="booleanPrimitive" value=""/>
    <p>01-${bean.booleanPrimitive}</p>
    <jsp:setProperty name="bean" property="booleanPrimitive" value="foo"/>
    <p>02-${bean.booleanPrimitive}</p>
    <jsp:setProperty name="bean" property="booleanPrimitive" value="true"/>
    <p>03-${bean.booleanPrimitive}</p>
    <jsp:setProperty name="bean" property="booleanPrimitive" value="TRuE"/>
    <p>04-${bean.booleanPrimitive}</p>
    <jsp:setProperty name="bean" property="booleanPrimitive" value="on"/>
    <p>05-${bean.booleanPrimitive}</p>

    <jsp:setProperty name="bean" property="booleanObject" value=""/>
    <p>11-${bean.booleanObject}</p>
    <jsp:setProperty name="bean" property="booleanObject" value="foo"/>
    <p>12-${bean.booleanObject}</p>
    <jsp:setProperty name="bean" property="booleanObject" value="true"/>
    <p>13-${bean.booleanObject}</p>
    <jsp:setProperty name="bean" property="booleanObject" value="TRuE"/>
    <p>14-${bean.booleanObject}</p>
    <jsp:setProperty name="bean" property="booleanPrimitive" value="on"/>
    <p>15-${bean.booleanPrimitive}</p>

    <jsp:setProperty name="bean" property="bytePrimitive" value=""/>
    <p>21-${bean.bytePrimitive}</p>
    <jsp:setProperty name="bean" property="bytePrimitive" value="42"/>
    <p>22-${bean.bytePrimitive}</p>
    <jsp:setProperty name="bean" property="bytePrimitive" value="-42"/>
    <p>23-${bean.bytePrimitive}</p>
    <jsp:setProperty name="bean" property="bytePrimitive" value="+42"/>
    <p>24-${bean.bytePrimitive}</p>

    <jsp:setProperty name="bean" property="byteObject" value=""/>
    <p>31-${bean.byteObject}</p>
    <jsp:setProperty name="bean" property="byteObject" value="42"/>
    <p>32-${bean.byteObject}</p>
    <jsp:setProperty name="bean" property="byteObject" value="-42"/>
    <p>33-${bean.byteObject}</p>
    <jsp:setProperty name="bean" property="byteObject" value="+42"/>
    <p>34-${bean.byteObject}</p>

    <jsp:setProperty name="bean" property="charPrimitive" value=""/>
    <p>41-${bean.charPrimitive}</p>
    <jsp:setProperty name="bean" property="charPrimitive" value="foo"/>
    <p>42-${bean.charPrimitive}</p>
    <jsp:setProperty name="bean" property="charPrimitive" value="b"/>
    <p>43-${bean.charPrimitive}</p>
    <jsp:setProperty name="bean" property="charPrimitive" value="
"/>
    <p>44-${bean.charPrimitive}</p>

    <jsp:setProperty name="bean" property="charObject" value=""/>
    <p>51-${bean.charObject}</p>
    <jsp:setProperty name="bean" property="charObject" value="foo"/>
    <p>52-${bean.charObject}</p>
    <jsp:setProperty name="bean" property="charObject" value="b"/>
    <p>53-${bean.charObject}</p>
    <jsp:setProperty name="bean" property="charObject" value="
"/>
    <p>54-${bean.charObject}</p>

    <jsp:setProperty name="bean" property="doublePrimitive" value=""/>
    <p>61-${bean.doublePrimitive}</p>
    <jsp:setProperty name="bean" property="doublePrimitive" value="42"/>
    <p>62-${bean.doublePrimitive}</p>
    <jsp:setProperty name="bean" property="doublePrimitive" value="-42"/>
    <p>63-${bean.doublePrimitive}</p>
    <jsp:setProperty name="bean" property="doublePrimitive" value="+42"/>
    <p>64-${bean.doublePrimitive}</p>

    <jsp:setProperty name="bean" property="doubleObject" value=""/>
    <p>71-${bean.doubleObject}</p>
    <jsp:setProperty name="bean" property="doubleObject" value="42"/>
    <p>72-${bean.doubleObject}</p>
    <jsp:setProperty name="bean" property="doubleObject" value="-42"/>
    <p>73-${bean.doubleObject}</p>
    <jsp:setProperty name="bean" property="doubleObject" value="+42"/>
    <p>74-${bean.doubleObject}</p>

    <jsp:setProperty name="bean" property="intPrimitive" value=""/>
    <p>81-${bean.intPrimitive}</p>
    <jsp:setProperty name="bean" property="intPrimitive" value="42"/>
    <p>82-${bean.intPrimitive}</p>
    <jsp:setProperty name="bean" property="intPrimitive" value="-42"/>
    <p>83-${bean.intPrimitive}</p>
    <jsp:setProperty name="bean" property="intPrimitive" value="+42"/>
    <p>84-${bean.intPrimitive}</p>

    <jsp:setProperty name="bean" property="intObject" value=""/>
    <p>91-${bean.intObject}</p>
    <jsp:setProperty name="bean" property="intObject" value="42"/>
    <p>92-${bean.intObject}</p>
    <jsp:setProperty name="bean" property="intObject" value="-42"/>
    <p>93-${bean.intObject}</p>
    <jsp:setProperty name="bean" property="intObject" value="+42"/>
    <p>94-${bean.intObject}</p>

    <jsp:setProperty name="bean" property="floatPrimitive" value=""/>
    <p>101-${bean.floatPrimitive}</p>
    <jsp:setProperty name="bean" property="floatPrimitive" value="42"/>
    <p>102-${bean.floatPrimitive}</p>
    <jsp:setProperty name="bean" property="floatPrimitive" value="-42"/>
    <p>103-${bean.floatPrimitive}</p>
    <jsp:setProperty name="bean" property="floatPrimitive" value="+42"/>
    <p>104-${bean.floatPrimitive}</p>

    <jsp:setProperty name="bean" property="floatObject" value=""/>
    <p>111-${bean.floatObject}</p>
    <jsp:setProperty name="bean" property="floatObject" value="42"/>
    <p>112-${bean.floatObject}</p>
    <jsp:setProperty name="bean" property="floatObject" value="-42"/>
    <p>113-${bean.floatObject}</p>
    <jsp:setProperty name="bean" property="floatObject" value="+42"/>
    <p>114-${bean.floatObject}</p>

    <jsp:setProperty name="bean" property="longPrimitive" value=""/>
    <p>121-${bean.longPrimitive}</p>
    <jsp:setProperty name="bean" property="longPrimitive" value="42"/>
    <p>122-${bean.longPrimitive}</p>
    <jsp:setProperty name="bean" property="longPrimitive" value="-42"/>
    <p>123-${bean.longPrimitive}</p>
    <jsp:setProperty name="bean" property="longPrimitive" value="+42"/>
    <p>124-${bean.longPrimitive}</p>

    <jsp:setProperty name="bean" property="longObject" value=""/>
    <p>131-${bean.longObject}</p>
    <jsp:setProperty name="bean" property="longObject" value="42"/>
    <p>132-${bean.longObject}</p>
    <jsp:setProperty name="bean" property="longObject" value="-42"/>
    <p>133-${bean.longObject}</p>
    <jsp:setProperty name="bean" property="longObject" value="+42"/>
    <p>134-${bean.longObject}</p>

    <jsp:setProperty name="bean" property="shortPrimitive" value=""/>
    <p>141-${bean.shortPrimitive}</p>
    <jsp:setProperty name="bean" property="shortPrimitive" value="42"/>
    <p>142-${bean.shortPrimitive}</p>
    <jsp:setProperty name="bean" property="shortPrimitive" value="-42"/>
    <p>143-${bean.shortPrimitive}</p>
    <jsp:setProperty name="bean" property="shortPrimitive" value="+42"/>
    <p>144-${bean.shortPrimitive}</p>

    <jsp:setProperty name="bean" property="shortObject" value=""/>
    <p>151-${bean.shortObject}</p>
    <jsp:setProperty name="bean" property="shortObject" value="42"/>
    <p>152-${bean.shortObject}</p>
    <jsp:setProperty name="bean" property="shortObject" value="-42"/>
    <p>153-${bean.shortObject}</p>
    <jsp:setProperty name="bean" property="shortObject" value="+42"/>
    <p>154-${bean.shortObject}</p>

    <jsp:setProperty name="bean" property="stringValue" value=""/>
    <p>161-${bean.stringValue}</p>
    <jsp:setProperty name="bean" property="stringValue" value="42"/>
    <p>162-${bean.stringValue}</p>
    <jsp:setProperty name="bean" property="stringValue" value="-42"/>
    <p>163-${bean.stringValue}</p>
    <jsp:setProperty name="bean" property="stringValue" value="+42"/>
    <p>164-${bean.stringValue}</p>

    <jsp:setProperty name="bean" property="objectValue" value=""/>
    <p>171-${bean.objectValue}</p>
    <jsp:setProperty name="bean" property="objectValue" value="42"/>
    <p>172-${bean.objectValue}</p>
    <jsp:setProperty name="bean" property="objectValue" value="-42"/>
    <p>173-${bean.objectValue}</p>
    <jsp:setProperty name="bean" property="objectValue" value="+42"/>
    <p>174-${bean.objectValue}</p>

    <jsp:setProperty name="bean" property="testerTypeA" value=""/>
    <p>181-${bean.testerTypeA}</p>
    <jsp:setProperty name="bean" property="testerTypeA" value="42"/>
    <p>182-${bean.testerTypeA}</p>
    <jsp:setProperty name="bean" property="testerTypeA" value="-42"/>
    <p>183-${bean.testerTypeA}</p>
    <jsp:setProperty name="bean" property="testerTypeA" value="+42"/>
    <p>184-${bean.testerTypeA}</p>

    <jsp:setProperty name="bean" property="testerTypeB" value=""/>
    <p>191-${bean.testerTypeB}</p>
  </body>
</html>

