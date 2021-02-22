/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.otel.bridge;

import java.net.URI;
import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.util.StringUtils;

/**
 * OpenTelemetry implementation of a {@link HttpServerHandler}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelHttpServerHandler
		extends HttpServerTracer<HttpServerRequest, HttpServerResponse, HttpServerRequest, HttpServerRequest>
		implements HttpServerHandler {

	private final HttpRequestParser httpServerRequestParser;

	private final HttpResponseParser httpServerResponseParser;

	private final Pattern pattern;

	public OtelHttpServerHandler(OpenTelemetry openTelemetry, HttpRequestParser httpServerRequestParser,
			HttpResponseParser httpServerResponseParser, SkipPatternProvider skipPatternProvider) {
		super(openTelemetry);
		this.httpServerRequestParser = httpServerRequestParser;
		this.httpServerResponseParser = httpServerResponseParser;
		this.pattern = skipPatternProvider.skipPattern();
	}

	@Override
	public Span handleReceive(HttpServerRequest request) {
		String url = request.path();
		boolean shouldSkip = !StringUtils.isEmpty(url) && this.pattern.matcher(url).matches();
		if (shouldSkip) {
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		Context context = startSpan(request, request, request, request.method());
		return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.fromContext(context), context);
	}

	@Override
	public void handleSend(HttpServerResponse response, Span span) {
		Throwable throwable = response.error();
		io.opentelemetry.api.trace.Span otel = OtelSpan.toOtel(span);
		try (Scope scope = otel.makeCurrent()) {
			parseResponse(span, response);
			if (throwable == null) {
				end(Context.current(), response);
			}
			else {
				endExceptionally(Context.current(), throwable, response);
			}
		}
	}

	private void parseResponse(Span span, HttpServerResponse response) {
		if (this.httpServerResponseParser != null) {
			this.httpServerResponseParser.parse(response, span.context(), span);
		}
	}

	@Override
	protected void onConnectionAndRequest(io.opentelemetry.api.trace.Span span, HttpServerRequest connection,
			HttpServerRequest request) {
		super.onConnectionAndRequest(span, connection, request);
		if (this.httpServerRequestParser != null) {
			Span fromOtel = OtelSpan.fromOtel(span);
			this.httpServerRequestParser.parse(request, fromOtel.context(), fromOtel);
		}
	}

	@Override
	protected void onRequest(io.opentelemetry.api.trace.Span span, HttpServerRequest request) {
		super.onRequest(span, request);
		String path = request.path();
		if (StringUtils.hasText(path)) {
			span.setAttribute("http.path", path);
		}
	}

	@Override
	public Context getServerContext(HttpServerRequest request) {
		Object context = request.getAttribute(CONTEXT_ATTRIBUTE);
		return context instanceof Context ? (Context) context : null;
	}

	@Override
	protected Integer peerPort(HttpServerRequest request) {
		return toUri(request).getPort();
	}

	@Override
	protected String peerHostIP(HttpServerRequest request) {
		return toUri(request).getHost();
	}

	@Override
	protected String flavor(HttpServerRequest request, HttpServerRequest request2) {
		return toUri(request).getScheme();
	}

	@Override
	protected TextMapPropagator.Getter<HttpServerRequest> getGetter() {
		return new TextMapPropagator.Getter<HttpServerRequest>() {
			@Override
			public Iterable<String> keys(HttpServerRequest carrier) {
				return carrier.headerNames();
			}

			@Override
			public String get(HttpServerRequest carrier, String key) {
				return carrier.header(key);
			}
		};
	}

	@Override
	protected String url(HttpServerRequest request) {
		return request.url();
	}

	protected URI toUri(HttpServerRequest request) {
		return URI.create(request.url());
	}

	@Override
	protected String method(HttpServerRequest request) {
		return request.method();
	}

	@Override
	protected String requestHeader(HttpServerRequest request, String s) {
		return request.header(s);
	}

	@Override
	protected int responseStatus(HttpServerResponse httpServerResponse) {
		return httpServerResponse.statusCode();
	}

	@Override
	protected void attachServerContext(Context context, HttpServerRequest request) {
		request.setAttribute(CONTEXT_ATTRIBUTE, context);
	}

	@Override
	protected String getInstrumentationName() {
		return "org.springframework.cloud.sleuth";
	}

}
