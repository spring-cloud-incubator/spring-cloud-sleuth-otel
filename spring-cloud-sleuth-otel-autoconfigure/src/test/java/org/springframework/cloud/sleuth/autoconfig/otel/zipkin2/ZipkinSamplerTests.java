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

package org.springframework.cloud.sleuth.autoconfig.otel.zipkin2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Not using {@linkplain SpringBootTest} as we need to change properties per test.
 *
 * @author Adrian Cole
 */
public class ZipkinSamplerTests {

	@Test
	void should_set_sampler_to_non_off_when_zipkin_handler_on_classpath_for_otel() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=AUTO")
				.withConfiguration(AutoConfigurations.of(TestConfig.class));

		contextRunner.run(context -> {
			io.opentelemetry.sdk.trace.samplers.Sampler sampler = context
					.getBean(io.opentelemetry.sdk.trace.samplers.Sampler.class);
			BDDAssertions.then(sampler).isNotSameAs(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff());
		});
	}

	@Test
	void should_set_service_name_to_zipkin_service_name() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withPropertyValues("spring.sleuth.tracer.mode=AUTO", "spring.application.name=foo",
						"spring.zipkin.service.name=bar")
				.withConfiguration(AutoConfigurations.of(TestConfig.class));

		contextRunner.run(context -> {
			Resource resource = context.getBean(Resource.class);
			BDDAssertions.then(resource.getAttributes().get(AttributeKey.stringKey("service.name"))).isEqualTo("bar");
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
