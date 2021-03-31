/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.jasper.runtime;

public class TesterBean {

    private boolean booleanPrimitive;
    private Boolean booleanObject;

    private byte bytePrimitive;
    private Byte byteObject;

    private char charPrimitive;
    private Character charObject;

    private double doublePrimitive;
    private Double doubleObject;

    private int intPrimitive;
    private Integer intObject;

    private float floatPrimitive;
    private Float floatObject;

    private long longPrimitive;
    private Long longObject;

    private short shortPrimitive;
    private Short shortObject;

    private String stringValue;

    private Object objectValue;

    private TesterTypeA testerTypeA;

    private TesterTypeB testerTypeB;


    public boolean getBooleanPrimitive() {
        return booleanPrimitive;
    }


    public void setBooleanPrimitive(boolean booleanPrimitive) {
        this.booleanPrimitive = booleanPrimitive;
    }


    public Boolean getBooleanObject() {
        return booleanObject;
    }


    public void setBooleanObject(Boolean booleanObject) {
        this.booleanObject = booleanObject;
    }


    public byte getBytePrimitive() {
        return bytePrimitive;
    }


    public void setBytePrimitive(byte bytePrimitive) {
        this.bytePrimitive = bytePrimitive;
    }


    public Byte getByteObject() {
        return byteObject;
    }


    public void setByteObject(Byte byteObject) {
        this.byteObject = byteObject;
    }


    public char getCharPrimitive() {
        return charPrimitive;
    }


    public void setCharPrimitive(char charPrimitive) {
        this.charPrimitive = charPrimitive;
    }


    public Character getCharObject() {
        return charObject;
    }


    public void setCharObject(Character charObject) {
        this.charObject = charObject;
    }


    public double getDoublePrimitive() {
        return doublePrimitive;
    }


    public void setDoublePrimitive(double doublePrimitive) {
        this.doublePrimitive = doublePrimitive;
    }


    public Double getDoubleObject() {
        return doubleObject;
    }


    public void setDoubleObject(Double doubleObject) {
        this.doubleObject = doubleObject;
    }


    public int getIntPrimitive() {
        return intPrimitive;
    }


    public void setIntPrimitive(int intPrimitive) {
        this.intPrimitive = intPrimitive;
    }


    public Integer getIntObject() {
        return intObject;
    }


    public void setIntObject(Integer intObject) {
        this.intObject = intObject;
    }


    public float getFloatPrimitive() {
        return floatPrimitive;
    }


    public void setFloatPrimitive(float floatPrimitive) {
        this.floatPrimitive = floatPrimitive;
    }


    public Float getFloatObject() {
        return floatObject;
    }


    public void setFloatObject(Float floatObject) {
        this.floatObject = floatObject;
    }


    public long getLongPrimitive() {
        return longPrimitive;
    }


    public void setLongPrimitive(long longPrimitive) {
        this.longPrimitive = longPrimitive;
    }


    public Long getLongObject() {
        return longObject;
    }


    public void setLongObject(Long longObject) {
        this.longObject = longObject;
    }


    public short getShortPrimitive() {
        return shortPrimitive;
    }


    public void setShortPrimitive(short shortPrimitive) {
        this.shortPrimitive = shortPrimitive;
    }


    public Short getShortObject() {
        return shortObject;
    }


    public void setShortObject(Short shortObject) {
        this.shortObject = shortObject;
    }


    public String getStringValue() {
        return stringValue;
    }


    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }


    public Object getObjectValue() {
        return objectValue;
    }


    public void setObjectValue(Object objectValue) {
        this.objectValue = objectValue;
    }


    public TesterTypeA getTesterTypeA() {
        return testerTypeA;
    }


    public void setTesterTypeA(TesterTypeA testerTypeA) {
        this.testerTypeA = testerTypeA;
    }


    public TesterTypeB getTesterTypeB() {
        return testerTypeB;
    }


    public void setTesterTypeB(TesterTypeB testerTypeB) {
        this.testerTypeB = testerTypeB;
    }


    public static class Inner {
        private String data;


        public String getData() {
            return data;
        }


        public void setData(String data) {
            this.data = data;
        }
    }


    @SuppressWarnings("unused")
    private static class Inner2 {
        public Inner2() {
        }
    }


    public abstract static class Inner4 {
    }
}
