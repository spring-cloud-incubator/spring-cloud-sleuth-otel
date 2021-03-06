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
import java.net.URISyntaxException;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * OpenTelemetry implementation of a {@link HttpClientHandler}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
public class OtelHttpClientHandler extends HttpClientTracer<HttpClientRequest, HttpClientRequest, HttpClientResponse>
		implements HttpClientHandler {

	private static final Log log = LogFactory.getLog(OtelHttpClientHandler.class);

	private final HttpRequestParser httpClientRequestParser;

	private final HttpResponseParser httpClientResponseParser;

	private final SamplerFunction<HttpRequest> samplerFunction;

	public OtelHttpClientHandler(OpenTelemetry openTelemetry, @Nullable HttpRequestParser httpClientRequestParser,
			@Nullable HttpResponseParser httpClientResponseParser, SamplerFunction<HttpRequest> samplerFunction) {
		super(openTelemetry, new NetPeerAttributes());
		this.httpClientRequestParser = httpClientRequestParser;
		this.httpClientResponseParser = httpClientResponseParser;
		this.samplerFunction = samplerFunction;
	}

	@Override
	public Context startSpan(Context parentContext, HttpClientRequest request, HttpClientRequest carrier,
			long startTimeNanos) {
		Context context = super.startSpan(parentContext, request, carrier, startTimeNanos);
		io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.fromContext(context);
		if (this.httpClientRequestParser != null) {
			Span fromOtel = OtelSpan.fromOtel(span);
			this.httpClientRequestParser.parse(request, fromOtel.context(), fromOtel);
		}
		String path = request.path();
		if (path != null) {
			span.setAttribute(SemanticAttributes.HTTP_ROUTE, path);
		}
		return context;
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("The sampler function filtered this request, will return an invalid span");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		Context context = startSpan(Context.current(), request, request);
		return span(request, context);
	}

	@Override
	public Span handleSend(HttpClientRequest request, TraceContext parent) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("Returning an invalid span since url [" + request.path() + "] is on a list of urls to skip");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		io.opentelemetry.api.trace.Span span = parent != null ? ((OtelTraceContext) parent).span() : null;
		if (span == null) {
			return span(request, startSpan(Context.current(), request, request));
		}
		try (Scope scope = span.makeCurrent()) {
			Context withParent = startSpan(Context.current(), request, request);
			return span(request, withParent);
		}
	}

	private Span span(HttpClientRequest request, Context context) {
		try (Scope scope = context.makeCurrent()) {
			io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
			String remoteIp = request.remoteIp();
			if (StringUtils.hasText(remoteIp)) {
				span.setAttribute(SemanticAttributes.NET_PEER_IP, remoteIp);
			}
			span.setAttribute(SemanticAttributes.NET_PEER_PORT, request.remotePort());
			return OtelSpan.fromOtel(span);
		}
	}

	@Override
	protected void onResponse(io.opentelemetry.api.trace.Span span, HttpClientResponse httpClientResponse) {
		super.onResponse(span, httpClientResponse);
		if (this.httpClientResponseParser != null) {
			Span fromOtel = OtelSpan.fromOtel(span);
			this.httpClientResponseParser.parse(httpClientResponse, fromOtel.context(), fromOtel);
		}
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		io.opentelemetry.api.trace.Span otelSpan = OtelSpan.toOtel(span);
		if (otelSpan.equals(io.opentelemetry.api.trace.Span.getInvalid())) {
			if (log.isDebugEnabled()) {
				log.debug("Not doing anything because the span is invalid");
			}
			return;
		}
		if (response.error() != null) {
			if (log.isDebugEnabled()) {
				log.debug("There was an error, will finish span [" + otelSpan + "] exceptionally");
			}
			endExceptionally(Context.current().with(otelSpan), response, response.error());
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("There was no error, will finish span [" + otelSpan + "] in a standard way");
			}
			end(Context.current().with(otelSpan), response);
		}
	}

	@Override
	protected String method(HttpClientRequest httpClientRequest) {
		return httpClientRequest.method();
	}

	@Override
	protected URI url(HttpClientRequest httpClientRequest) throws URISyntaxException {
		return URI.create(httpClientRequest.url());
	}

	@Override
	protected Integer status(HttpClientResponse httpClientResponse) {
		return httpClientResponse.statusCode();
	}

	@Override
	protected String requestHeader(HttpClientRequest httpClientRequest, String s) {
		return httpClientRequest.header(s);
	}

	@Override
	protected String responseHeader(HttpClientResponse httpClientResponse, String s) {
		return httpClientResponse.header(s);
	}

	@Override
	protected TextMapSetter<HttpClientRequest> getSetter() {
		return HttpClientRequest::header;
	}

	@Override
	protected String getInstrumentationName() {
		return "org.springframework.cloud.sleuth";
	}

}
