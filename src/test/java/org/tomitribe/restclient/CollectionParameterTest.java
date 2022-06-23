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

import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CollectionParameterTest {

    private final Client client = new Client();
    private final CollectionClient collectionOfString = client.get(CollectionClient.class);

    @Test
    public void queryParamMethod() {
        collectionOfString.queryParam(Arrays.asList(2, 3, 5, 7, 11, 13, 17));

        assertParam(Request::getQueryParams, "2,3,5,7,11,13,17");
    }

    @Test
    public void headerParamMethod() {
        collectionOfString.headerParam(Arrays.asList(2, 3, 5, 7, 11, 13, 17));

        final Request<?> request = client.getRequest();
        assertEquals("2,3,5,7,11,13,17", request.getHeaderParams().get("excluded"));
    }

    @Test
    public void pathParamMethod() {
        collectionOfString.pathParam(Arrays.asList(2, 3, 5, 7, 11, 13, 17));

        final Request<?> request = client.getRequest();
        assertEquals("2,3,5,7,11,13,17", request.getPathParams().get("excluded"));
    }

    private void assertParam(final Function<Request<?>, Map<String, String>> foo, final String expected) {
        final Map<String, String> map = foo.apply(client.getRequest());
        assertEquals(expected, map.get("excluded"));
    }


    interface CollectionClient {
        @GET
        @Path("/excluded")
        void queryParam(@QueryParam("excluded") final List<Integer> excluded);

        @GET
        @Path("/excluded")
        void headerParam(@HeaderParam("excluded") final List<Integer> excluded);

        @GET
        @Path("/excluded")
        void pathParam(@PathParam("excluded") final List<Integer> excluded);
    }
}
