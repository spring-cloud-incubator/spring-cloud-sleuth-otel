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

package org.springframework.cloud.sleuth.autoconfig;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
class NoOpHttpClientHandler implements HttpClientHandler {

	@Override
	public Span handleSend(HttpClientRequest request) {
		return new NoOpSpan();
	}

	@Override
	public Span handleSend(HttpClientRequest request, TraceContext parent) {
		return new NoOpSpan();
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {

	}

}
