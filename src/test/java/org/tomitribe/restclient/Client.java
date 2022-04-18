/*
 * Copyright 2022 Tomitribe and community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tomitribe.restclient;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

public class Client {
    private final AtomicReference<Request<?>> request = new AtomicReference<>();

    public <T> T get(final Class<T> clazz) {
        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{clazz}, (proxy, method, args) -> {
                    final Request<?> from = Request.from(method, args);
                    request.set(from);
                    return null;
                }
        );
    }

    public Request<?> getRequest() {
        return request.get();
    }
}
