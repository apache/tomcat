package org.apache.catalina.core;

public class ApplicationMappingImpl {

    private final String matchValue;
    private final String pattern;
    private final ApplicationMappingMatch mappingType;
    private final String servletName;

    public ApplicationMappingImpl(String matchValue, String pattern, ApplicationMappingMatch mappingType, String servletName) {
        this.matchValue = matchValue;
        this.pattern = pattern;
        this.mappingType = mappingType;
        this.servletName = servletName;
    }


    public String getMatchValue() {
        return matchValue;
    }


    public String getPattern() {
        return pattern;
    }


    public ApplicationMappingMatch getMappingMatch() {
        return mappingType;
    }


    public String getServletName() {
        return servletName;
    }
}
