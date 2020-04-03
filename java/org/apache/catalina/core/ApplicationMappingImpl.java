package org.apache.catalina.core;

import org.apache.catalina.servlet4preview.http.HttpServletMapping;

public class ApplicationMappingImpl implements HttpServletMapping {

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

    @Override
    public String getMatchValue() {
        return matchValue;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    @Override
    public ApplicationMappingMatch getMappingMatch() {
        return mappingType;
    }

    @Override
    public String getServletName() {
        return servletName;
    }
}
