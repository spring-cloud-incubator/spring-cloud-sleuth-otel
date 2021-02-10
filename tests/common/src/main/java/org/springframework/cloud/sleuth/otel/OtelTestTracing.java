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

package org.springframework.cloud.sleuth.otel;

import java.io.Closeable;
import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.otel.bridge.ArrayListSpanProcessor;
import org.springframework.cloud.sleuth.otel.bridge.OtelAccessor;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.cloud.sleuth.test.TestSpanHandler;
import org.springframework.cloud.sleuth.test.TestTracingAssertions;
import org.springframework.cloud.sleuth.test.TestTracingAware;
import org.springframework.cloud.sleuth.test.TestTracingAwareSupplier;
import org.springframework.cloud.sleuth.test.TracerAware;
import org.springframework.context.ApplicationEventPublisher;

public class OtelTestTracing implements TracerAware, TestTracingAware, TestTracingAwareSupplier, Closeable {

	public OtelTestTracing() {
		initializeOtel();
	}

	ArrayListSpanProcessor spanProcessor = new ArrayListSpanProcessor();

	final ContextPropagators contextPropagators = contextPropagators();

	Sampler sampler = Sampler.alwaysOn();

	HttpRequestParser clientRequestParser;

	io.opentelemetry.api.OpenTelemetry openTelemetry = initializeOtel();

	CurrentTraceContext currentTraceContext = OtelAccessor.currentTraceContext(publisher());

	protected ContextPropagators contextPropagators() {
		return ContextPropagators.create(B3Propagator.builder().injectMultipleHeaders().build());
	}

	OpenTelemetry initializeOtel() {
		return OpenTelemetrySdk.builder().setTracerProvider(otelTracerProvider())
				.setPropagators(this.contextPropagators).build();
	}

	SdkTracerProvider otelTracerProvider() {
		return SdkTracerProvider.builder().build();
	}

	private void reset() {
		this.openTelemetry = initializeOtel();
		this.currentTraceContext = OtelAccessor.currentTraceContext(publisher());
	}

	@Override
	public TracerAware sampler(TraceSampler sampler) {
		this.sampler = sampler == TraceSampler.ON ? Sampler.alwaysOn() : Sampler.alwaysOff();
		return this;
	}

	@Override
	public TracerAware tracing() {
		return this;
	}

	@Override
	public TestSpanHandler handler() {
		return new OtelTestSpanHandler(this.spanProcessor);
	}

	@Override
	public TestTracingAssertions assertions() {
		return new OtelTestTracingAssertions();
	}

	@Override
	public void close() {
		this.spanProcessor.clear();
		this.sampler = Sampler.alwaysOn();
	}

	@Override
	public TestTracingAware tracerTest() {
		return this;
	}

	@Override
	public Tracer tracer() {
		reset();
		return OtelAccessor.tracer(openTelemetry, this.currentTraceContext, new SleuthBaggageProperties(), publisher());
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		reset();
		return OtelAccessor.currentTraceContext(publisher());
	}

	@Override
	public Propagator propagator() {
		reset();
		return OtelAccessor.propagator(this.contextPropagators, openTelemetry);
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		reset();
		return OtelAccessor.httpServerHandler(openTelemetry, null, null, () -> Pattern.compile(""));
	}

	@Override
	public TracerAware clientRequestParser(HttpRequestParser httpRequestParser) {
		this.clientRequestParser = httpRequestParser;
		return this;
	}

	@Override
	public HttpClientHandler httpClientHandler() {
		reset();
		return OtelAccessor.httpClientHandler(openTelemetry, this.clientRequestParser, null,
				SamplerFunction.alwaysSample());
	}

	ApplicationEventPublisher publisher() {
		return event -> {

		};
	}

}
