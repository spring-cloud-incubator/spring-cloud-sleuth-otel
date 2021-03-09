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
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompositeTextMapPropagatorTest {

	@Test
	void extract_onlyBaggage() {
		BeanFactory beanFactory = mock(BeanFactory.class, Mockito.RETURNS_DEEP_STUBS);

		CompositeTextMapPropagator compositeTextMapPropagator = new CompositeTextMapPropagator(beanFactory,
				Collections.singletonList(PropagationType.W3C));

		Map<String, String> carrier = new HashMap<>();
		carrier.put("baggage", "key=value");
		Context result = compositeTextMapPropagator.extract(Context.root(), carrier, new MapGetter());

		assertThat(Baggage.fromContextOrNull(result)).isNotNull();
		assertThat(Baggage.fromContext(result)).isEqualTo(Baggage.builder().put("key", "value").build());
	}

	private static class MapGetter implements TextMapGetter<Map<String, String>> {

		@Override
		public Iterable<String> keys(Map<String, String> carrier) {
			return carrier.keySet();
		}

		@Override
		public String get(Map<String, String> carrier, String key) {
			return carrier.get(key);
		}

	}

}
