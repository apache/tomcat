/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;

import java.io.IOException;

import java.util.ResourceBundle;
import java.util.MissingResourceException;

import java.net.URLEncoder;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>Implementation of a Valve that forwards to jsps.</p>
 *
 * <p>This Valve should be attached at the Host level, although it will work
 * if attached to a Context.</p>
 *
 */
public class RedirectErrorReportValve extends ErrorReportValve {
	private static final Log log = LogFactory.getLog(RedirectErrorReportValve.class);

	public RedirectErrorReportValve() {
		super();
	}

	@Override
	protected void report(Request request, Response response, Throwable throwable) {
		try {
			reportImpl(request, response, throwable);
		} catch(Throwable t) {
			ExceptionUtils.handleThrowable(t);
			log.warn("Returning error reporting to "+super.getClass().getName()+".", t);
			super.report(request, response, throwable);
		}
	}

	private String getRedirectUrl(Response response) {
		int statusCode = response.getStatus();

		ResourceBundle resourceBundle = ResourceBundle.getBundle(this.getClass().getSimpleName(), response.getLocale());

		String redirectUrl = null;
		try {
			redirectUrl = resourceBundle.getString(""+statusCode);
		} catch(MissingResourceException e) {
			redirectUrl = resourceBundle.getString("default");
		}

		return redirectUrl;
	}

	private void reportImpl(Request request, Response response, Throwable throwable) throws Throwable {
		int statusCode = response.getStatus();

		// Do nothing on a 1xx, 2xx and 3xx status
		// Do nothing if anything has been written already
		// Do nothing if the response hasn't been explicitly marked as in error
		//	and that error has not been reported.
		if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
			return;
		}

		String urlString = getRedirectUrl(response)+"?requestUri="+request.getRequestURI()+"&statusCode="+statusCode;

		try {
			StringManager smClient = StringManager.getManager( Constants.Package, request.getLocales());
			String statusDescription = smClient.getString("http." + statusCode);
			urlString+="&statusDescription="+URLEncoder.encode(statusDescription);
		} catch(Exception e) {
			log.warn("Failed to get status description for "+statusCode, e);
		}

		if(null != throwable) {
			urlString+="&throwable="+URLEncoder.encode(throwable.toString());
		}

		response.sendRedirect(urlString);
	}
}

