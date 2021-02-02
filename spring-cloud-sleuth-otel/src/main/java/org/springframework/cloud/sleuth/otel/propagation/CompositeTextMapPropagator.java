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

package org.springframework.cloud.sleuth.otel.propagation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.AwsXrayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracerPropagator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;

/**
 * {@link TextMapPropagator} implementation that can read / write from multiple
 * {@link TextMapPropagator} implementations.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class CompositeTextMapPropagator implements TextMapPropagator {

	private static final Log log = LogFactory.getLog(CompositeTextMapPropagator.class);

	private final Map<PropagationType, TextMapPropagator> mapping = new HashMap<>();

	private final List<PropagationType> types;

	public CompositeTextMapPropagator(BeanFactory beanFactory, List<PropagationType> types) {
		this.types = types;
		if (isOnClasspath("io.opentelemetry.extension.trace.propagation.AwsXRayPropagator")) {
			this.mapping.put(PropagationType.AWS, beanFactory.getBeanProvider(AwsXrayPropagator.class)
					.getIfAvailable(AwsXrayPropagator::getInstance));
		}
		if (isOnClasspath("io.opentelemetry.extension.trace.propagation.B3Propagator")) {
			this.mapping.put(PropagationType.B3,
					beanFactory.getBeanProvider(B3Propagator.class).getIfAvailable(B3Propagator::getInstance));
		}
		if (isOnClasspath("io.opentelemetry.extension.trace.propagation.JaegerPropagator")) {
			this.mapping.put(PropagationType.JAEGER,
					beanFactory.getBeanProvider(JaegerPropagator.class).getIfAvailable(JaegerPropagator::getInstance));
		}
		if (isOnClasspath("io.opentelemetry.extension.trace.propagation.OtTracerPropagator")) {
			this.mapping.put(PropagationType.OT_TRACER, beanFactory.getBeanProvider(OtTracerPropagator.class)
					.getIfAvailable(OtTracerPropagator::getInstance));
		}
		this.mapping.put(PropagationType.W3C, TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(),
				W3CBaggagePropagator.getInstance()));
		this.mapping.put(PropagationType.CUSTOM, NoopTextMapPropagator.INSTANCE);
		log.info("Registered the following context propagation types " + this.mapping.keySet());
	}

	private boolean isOnClasspath(String clazz) {
		return ClassUtils.isPresent(clazz, null);
	}

	@Override
	public List<String> fields() {
		return this.types.stream().map(key -> this.mapping.getOrDefault(key, NoopTextMapPropagator.INSTANCE))
				.flatMap(p -> p.fields().stream()).collect(Collectors.toList());
	}

	@Override
	public <C> void inject(Context context, C carrier, Setter<C> setter) {
		this.types.stream().map(key -> this.mapping.getOrDefault(key, NoopTextMapPropagator.INSTANCE))
				.forEach(p -> p.inject(context, carrier, setter));
	}

	@Override
	public <C> Context extract(Context context, C carrier, Getter<C> getter) {
		for (PropagationType type : this.types) {
			TextMapPropagator propagator = this.mapping.get(type);
			if (propagator == null || propagator == NoopTextMapPropagator.INSTANCE) {
				continue;
			}
			Context extractedContext = propagator.extract(context, carrier, getter);
			Span span = Span.fromContextOrNull(extractedContext);
			if (span != null) {
				return extractedContext;
			}
		}
		return context;
	}

	private static final class NoopTextMapPropagator implements TextMapPropagator {

		private static final NoopTextMapPropagator INSTANCE = new NoopTextMapPropagator();

		@Override
		public List<String> fields() {
			return Collections.emptyList();
		}

		@Override
		public <C> void inject(Context context, C carrier, Setter<C> setter) {
		}

		@Override
		public <C> Context extract(Context context, C carrier, Getter<C> getter) {
			return context;
		}

	}

}
