package org.apache.catalina;



public interface Executor extends java.util.concurrent.Executor, Lifecycle {
    public String getName();
}