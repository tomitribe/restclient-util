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

import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NoBodyTest {

    private final Color color = Color.builder()
            .red(255)
            .green(165)
            .blue(0)
            .build();
    private final Client client = new Client();
    private final ColorClient colorClient = client.get(ColorClient.class);

    @Test
    public void get() {

        colorClient.get(color);

        final Request<?> request = client.getRequest();
        assertNull(request.getBody());
        assertEquals(255, request.getQueryParams().get("red"));
        assertEquals(165, request.getQueryParams().get("green"));
        assertEquals(0, request.getQueryParams().get("blue"));
    }

    @Test
    public void put() {

        colorClient.put(color);

        final Request<?> request = client.getRequest();
        assertNull(request.getBody());
        assertEquals(Request.Method.PUT, request.getMethod());
        assertEquals(255, request.getQueryParams().get("red"));
        assertEquals(165, request.getQueryParams().get("green"));
        assertEquals(0, request.getQueryParams().get("blue"));
    }

    public interface ColorClient {

        @GET
        @Path("/color")
        void get(final Color color);

        @PUT
        @Path("/color")
        void put(final Color color);
    }

    @Data
    @Builder
    public static class Color {

        @QueryParam("red")
        private Integer red;

        @QueryParam("green")
        private Integer green;

        @QueryParam("blue")
        private Integer blue;
    }

}
