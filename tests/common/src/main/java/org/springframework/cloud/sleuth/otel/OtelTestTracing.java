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

import io.opentelemetry.api.GlobalOpenTelemetry;
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
		// the global instance is used by the instrumentation APIs internally, so we need
		// to initialize it.
		initializeOtelGlobal();
	}

	ArrayListSpanProcessor spanProcessor = new ArrayListSpanProcessor();

	ContextPropagators contextPropagators = contextPropagators();

	Sampler sampler = Sampler.alwaysOn();

	HttpRequestParser clientRequestParser;

	io.opentelemetry.api.trace.Tracer tracer = otelTracer();

	CurrentTraceContext currentTraceContext = OtelAccessor.currentTraceContext(publisher());

	io.opentelemetry.api.trace.Tracer otelTracer() {
		SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(this.spanProcessor)
				.setSampler(this.sampler).build();
		return provider.get("org.springframework.cloud.sleuth");
	}

	protected ContextPropagators contextPropagators() {
		return ContextPropagators.create(B3Propagator.builder().injectMultipleHeaders().build());
	}

	OpenTelemetry initializeOtelGlobal() {
		GlobalOpenTelemetry.resetForTest();
		OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(otelTracerProvider())
				.setPropagators(this.contextPropagators).build();
		GlobalOpenTelemetry.set(openTelemetry);
		return openTelemetry;
	}

	SdkTracerProvider otelTracerProvider() {
		return SdkTracerProvider.builder().build();
	}

	private void reset() {
		this.contextPropagators = contextPropagators();
		this.tracer = otelTracer();
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
		return OtelAccessor.tracer(this.tracer, this.currentTraceContext, new SleuthBaggageProperties(), publisher());
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		reset();
		return OtelAccessor.currentTraceContext(publisher());
	}

	@Override
	public Propagator propagator() {
		reset();
		return OtelAccessor.propagator(this.contextPropagators, this.tracer);
	}

	@Override
	public HttpServerHandler httpServerHandler() {
		reset();
		return OtelAccessor.httpServerHandler(this.tracer, null, null, () -> Pattern.compile(""));
	}

	@Override
	public TracerAware clientRequestParser(HttpRequestParser httpRequestParser) {
		this.clientRequestParser = httpRequestParser;
		return this;
	}

	@Override
	public HttpClientHandler httpClientHandler() {
		reset();
		return OtelAccessor.httpClientHandler(this.tracer, this.clientRequestParser, null,
				SamplerFunction.alwaysSample());
	}

	ApplicationEventPublisher publisher() {
		return event -> {

		};
	}

}
