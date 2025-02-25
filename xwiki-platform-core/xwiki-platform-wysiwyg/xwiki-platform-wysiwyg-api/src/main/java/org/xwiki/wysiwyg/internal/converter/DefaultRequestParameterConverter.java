/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.wysiwyg.internal.converter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.wysiwyg.converter.HTMLConverter;
import org.xwiki.wysiwyg.converter.RequestParameterConverter;
import org.xwiki.wysiwyg.filter.MutableServletRequest;
import org.xwiki.wysiwyg.filter.MutableServletRequestFactory;

/**
 * Default implementation of {@link RequestParameterConverter} that handles HTML conversion of parameters needing such
 * conversion.
 *
 * @version $Id$
 * @since 13.5RC1
 */
@Component
@Singleton
public class DefaultRequestParameterConverter implements RequestParameterConverter
{
    /**
     * The name of the request parameter whose multiple values indicate the request parameters that require HTML
     * conversion. For instance, if this parameter's value is {@code [description, content]} then the request has two
     * parameters, {@code description} and {@code content}, requiring HTML conversion. The syntax these parameters must
     * be converted to is found also on the request, under {@code description_syntax} and {@code content_syntax}
     * parameters.
     */
    private static final String REQUIRES_HTML_CONVERSION = "RequiresHTMLConversion";

    /**
     * The name of the session attribute holding the conversion output. The conversion output is stored in a {@link Map}
     * of {@link Map}s. The first key identifies the request and the second key is the name of the request parameter
     * that required HTML conversion.
     */
    private static final String CONVERSION_OUTPUT = "com.xpn.xwiki.wysiwyg.server.converter.output";

    /**
     * The name of the session attribute holding the conversion exceptions. The conversion exceptions are stored in a
     * {@link Map} of {@link Map}s. The first key identifies the request and the second key is the name of the request
     * parameter that required HTML conversion.
     */
    private static final String CONVERSION_ERRORS = "com.xpn.xwiki.wysiwyg.server.converter.errors";

    @Inject
    private MutableServletRequestFactory mutableServletRequestFactory;

    @Inject
    private HTMLConverter htmlConverter;

    @Inject
    private Logger logger;

    @Override
    public Optional<ServletRequest> convert(ServletRequest request, ServletResponse response) throws IOException
    {
        Optional<ServletRequest> result = Optional.of(request);
        // Take the list of request parameters that require HTML conversion.
        String[] parametersRequiringHTMLConversion = request.getParameterValues(REQUIRES_HTML_CONVERSION);
        if (parametersRequiringHTMLConversion != null) {
            // Wrap the current request in order to be able to change request parameters.
            MutableServletRequest mreq = this.mutableServletRequestFactory.newInstance(request);
            // Remove the list of request parameters that require HTML conversion to avoid recurrency.
            mreq.removeParameter(REQUIRES_HTML_CONVERSION);
            // Try to convert each parameter from the list and save caught exceptions.
            Map<String, Throwable> errors = new HashMap<>();
            // Save also the output to prevent loosing data in case of conversion exceptions.
            Map<String, String> output = new HashMap<>();
            convertHTML(parametersRequiringHTMLConversion, mreq, output, errors);
            if (!errors.isEmpty()) {
                handleConversionErrors(errors, output, mreq, response);
                result = Optional.empty();
            } else {
                result = Optional.of(mreq);
            }
        }
        return result;
    }

    private void convertHTML(String[] parametersRequiringHTMLConversion, MutableServletRequest request,
        Map<String, String> output, Map<String, Throwable> errors)
    {
        for (String parameterName : parametersRequiringHTMLConversion) {
            String html = request.getParameter(parameterName);
            // Remove the syntax parameter from the request to avoid interference with further request processing.
            String syntax = request.removeParameter(parameterName + "_syntax");
            if (html == null || syntax == null) {
                continue;
            }
            try {
                request.setParameter(parameterName, this.htmlConverter.fromHTML(html, syntax));
            } catch (Exception e) {
                this.logger.error(e.getLocalizedMessage(), e);
                errors.put(parameterName, e);
            }
            // If the conversion fails the output contains the value before the conversion.
            output.put(parameterName, request.getParameter(parameterName));
        }
    }

    private void handleConversionErrors(Map<String, Throwable> errors, Map<String, String> output,
        MutableServletRequest mreq, ServletResponse res) throws IOException
    {
        ServletRequest req = mreq.getRequest();
        if (req instanceof HttpServletRequest
            && "XMLHttpRequest".equals(((HttpServletRequest) req).getHeader("X-Requested-With"))) {
            // If this is an AJAX request then we should simply send back the error.
            StringBuilder errorMessage = new StringBuilder();
            // Aggregate all error messages (for all fields that have conversion errors).
            for (Map.Entry<String, Throwable> entry : errors.entrySet()) {
                errorMessage.append(entry.getKey()).append(": ");
                errorMessage.append(entry.getValue().getLocalizedMessage()).append('\n');
            }
            ((HttpServletResponse) res).sendError(400, errorMessage.substring(0, errorMessage.length() - 1));
            return;
        }
        // Otherwise, if this is a normal request, we have to redirect the request back and provide a key to
        // access the exception and the value before the conversion from the session.
        // Redirect to the error page specified on the request.
        String redirectURL = mreq.getParameter("xerror");
        if (redirectURL == null) {
            // Redirect to the referrer page.
            redirectURL = mreq.getReferer();
        }
        // Extract the query string.
        String queryString = StringUtils.substringAfterLast(redirectURL, String.valueOf('?'));
        // Remove the query string.
        redirectURL = StringUtils.substringBeforeLast(redirectURL, String.valueOf('?'));
        // Remove the previous key from the query string. We have to do this since this might not be the first
        // time the conversion fails for this redirect URL.
        queryString = queryString.replaceAll("key=.*&?", "");
        if (queryString.length() > 0 && !queryString.endsWith(String.valueOf('&'))) {
            queryString += '&';
        }
        // Save the output and the caught exceptions on the session.
        queryString += "key=" + save(mreq, output, errors);
        mreq.sendRedirect(res, redirectURL + '?' + queryString);
    }

    /**
     * Saves on the session the conversion output and the caught conversion exceptions, after a conversion failure.
     *
     * @param mreq the request used to access the session
     * @param output the conversion output for the given request
     * @param errors the conversion exceptions for the given request
     * @return a key that can be used along with the name of the request parameters that required HTML conversion to
     *         extract the conversion output and the conversion exceptions from the {@link #CONVERSION_OUTPUT} and
     *         {@value #CONVERSION_ERRORS} session attributes
     */
    @SuppressWarnings("unchecked")
    private String save(MutableServletRequest mreq, Map<String, String> output, Map<String, Throwable> errors)
    {
        // Generate a random key to identify the request.
        String key = RandomStringUtils.randomAlphanumeric(4);

        // Save the output on the session.
        Map<String, Map<String, String>> conversionOutput =
            (Map<String, Map<String, String>>) mreq.getSessionAttribute(CONVERSION_OUTPUT);
        if (conversionOutput == null) {
            conversionOutput = new HashMap<String, Map<String, String>>();
            mreq.setSessionAttribute(CONVERSION_OUTPUT, conversionOutput);
        }
        conversionOutput.put(key, output);

        // Save the errors on the session.
        Map<String, Map<String, Throwable>> conversionErrors =
            (Map<String, Map<String, Throwable>>) mreq.getSessionAttribute(CONVERSION_ERRORS);
        if (conversionErrors == null) {
            conversionErrors = new HashMap<String, Map<String, Throwable>>();
            mreq.setSessionAttribute(CONVERSION_ERRORS, conversionErrors);
        }
        conversionErrors.put(key, errors);

        return key;
    }
}
