/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.liveSense.service.languageselector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.mime.MimeTypeService;
import org.liveSense.core.Configurator;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>LanguageSelectorServlet</code> 
 *   
 */
@Component(immediate=true, label="%language.servlet.name", description="%language.servlet.description")
@Service(javax.servlet.Servlet.class)
@Properties(value={
	@Property(name="sling.servlet.paths", value="/session/language"),
	@Property(name="sling.servlet.methods", value={"GET", "POST"})
})



public class LanguageSelectorServlet extends HttpServlet {

   	/**
	 * 
	 */
	private static final long serialVersionUID = 8016477105020492838L;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
   	LanguageSelectorService languageSelector;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
   	Configurator configurator;

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
   	MimeTypeService mimeTypeService;

	static final int DEFAULT_BUFFER_SIZE = 4096;

	static final String STORE_TYPE_COOKIE = "cookie";
	static final String STORE_TYPE_SESSION = "session";

	//HashMap<String,String> = mimenew HashMap<String, String>();

	/** default log */
	private final Logger log = LoggerFactory.getLogger(LanguageSelectorServlet.class);


	public static final String PARAM_STORE_TYPE = "storeType";
	public static final String DEFAULT_STORE_TYPE = STORE_TYPE_COOKIE;
	@Property(name=PARAM_STORE_TYPE, label="%soreType.name", description="%storeType.description", value=DEFAULT_STORE_TYPE)
	private String storeType = DEFAULT_STORE_TYPE;

	/**
	 * Activates this component.
	 *
	 * @param componentContext The OSGi <code>ComponentContext</code> of this
	 *            component.
	 */
	@Activate
	protected void activate(ComponentContext componentContext) {
		@SuppressWarnings("unused")
		Dictionary<?, ?> props = componentContext.getProperties();

		String storeTypeNew = (String) componentContext.getProperties().get(PARAM_STORE_TYPE);
		if (storeTypeNew == null || storeTypeNew.length() == 0) {
			storeTypeNew = DEFAULT_STORE_TYPE;
		}
		if (!storeTypeNew.equals(this.storeType)) {
			log.info("Setting new storeType {} (was {})", storeTypeNew, this.storeType);
			this.storeType = storeTypeNew;
		}
	}

	/**
	 * Copies the contents of the given {@link InputStream}
	 * to the given {@link OutputStream}. Shortcut for
	 * <pre>
	 *   copy(pInputStream, pOutputStream, new byte[8192]);
	 * </pre>
	 * @param pInputStream The input stream, which is being read.
	 * It is guaranteed, that {@link InputStream#close()} is called
	 * on the stream.
	 * @param pOutputStream The output stream, to which data should
	 * be written. May be null, in which case the input streams
	 * contents are simply discarded.
	 * @param pClose True guarantees, that {@link OutputStream#close()}
	 * is called on the stream. False indicates, that only
	 * {@link OutputStream#flush()} should be called finally.
	 *
	 * @return Number of bytes, which have been copied.
	 * @throws IOException An I/O error occurred.
	 */
	public static long copy(InputStream pInputStream,
			OutputStream pOutputStream, boolean pClose)
			throws IOException {
		return copy(pInputStream, pOutputStream, pClose,
				new byte[DEFAULT_BUFFER_SIZE]);
	}

	/**
	 * Copies the contents of the given {@link InputStream}
	 * to the given {@link OutputStream}.
	 * @param pIn The input stream, which is being read.
	 *   It is guaranteed, that {@link InputStream#close()} is called
	 *   on the stream.
	 * @param pOut The output stream, to which data should
	 *   be written. May be null, in which case the input streams
	 *   contents are simply discarded.
	 * @param pClose True guarantees, that {@link OutputStream#close()}
	 *   is called on the stream. False indicates, that only
	 *   {@link OutputStream#flush()} should be called finally.
	 * @param pBuffer Temporary buffer, which is to be used for
	 *   copying data.
	 * @return Number of bytes, which have been copied.
	 * @throws IOException An I/O error occurred.
	 */
	public static long copy(InputStream pIn,
			OutputStream pOut, boolean pClose,
			byte[] pBuffer)
			throws IOException {
		OutputStream out = pOut;
		InputStream in = pIn;
		try {
			long total = 0;
			for (;;) {
				int res = in.read(pBuffer);
				if (res == -1) {
					break;
				}
				if (res > 0) {
					total += res;
					if (out != null) {
						out.write(pBuffer, 0, res);
					}
				}
			}
			if (out != null) {
				if (pClose) {
					out.close();
				} else {
					out.flush();
				}
				out = null;
			}
			in.close();
			in = null;
			return total;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (Throwable t) {
					/* Ignore me */
				}
			}
			if (pClose && out != null) {
				try {
					out.close();
				} catch (Throwable t) {
					/* Ignore me */
				}
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		String locale = null;
		String type = null;
		String size = null;
		String imageType = null;
		if (request.getParameter("loc") != null) {
			locale = request.getParameter("loc");
		}
		if (request.getParameter("type") != null) {
			type = request.getParameter("type");
		}
		if (request.getParameter("size") != null) {
			size = request.getParameter("size");
		}
		if (request.getParameter("imagetype") != null) {
			imageType = request.getParameter("imagetype");
		}

		// Streaming the image
		if (locale != null && type != null && imageType != null && size != null && type.equalsIgnoreCase("flag")) {

			BufferedInputStream bisr = null;
			try {
				bisr = new BufferedInputStream(languageSelector.getFlag(locale, size, imageType));	
			} catch (Throwable e) {
			}
			if (bisr != null) {
				//response.setHeader("Cache-Control", "no-store");
				//response.setHeader("Pragma", "no-cache");
				response.setHeader("Cache-Control", "max-age=3600, must-revalidate");
				response.addDateHeader("Expires", System.currentTimeMillis() + 1000*60*60*1);
				//response.setDateHeader("Expires", 0);

				response.setContentType(mimeTypeService.getMimeType(imageType));

				copy(bisr, response.getOutputStream(),false);

				response.getOutputStream().flush();
				response.getOutputStream().close();
			} else {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		} else if (request.getParameter("locale") != null) {
			String storeType = this.storeType;
			if (request.getParameter("store") != null) {
				storeType = request.getParameter("store");
			}

			if (storeType.equals(STORE_TYPE_COOKIE)) {
				Cookie cookie = new Cookie(languageSelector.getStoreKeyName(), request.getParameter("locale"));

				if (request.getParameter("domain") != null) {
					cookie.setDomain(request.getParameter("domain"));					
				}
				cookie.setMaxAge(-1);
				cookie.setPath("/");
				cookie.setSecure(request.isSecure());
				response.addCookie(cookie);
			} else if (storeType.equals(STORE_TYPE_SESSION)) {
				request.getSession(true).setAttribute(languageSelector.getStoreKeyName(), request.getParameter("locale"));
			}
		}
		response.getWriter().write("<html><head><meta http-equiv=\"refresh\" content=\"0;url="+request.getHeader("referer")+"\"/></head><body/></html>");
		response.setContentType("text/html");
	}


	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		doGet(request, response);
	}
}
