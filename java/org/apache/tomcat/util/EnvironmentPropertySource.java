package org.apache.tomcat.util;

public class EnvironmentPropertySource implements IntrospectionUtils.PropertySource {
	@Override
	public String getProperty(String key) {
		return System.getenv(key);
	}
}
