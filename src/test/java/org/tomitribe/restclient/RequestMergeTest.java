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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import javax.json.bind.annotation.JsonbProperty;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.tomitribe.restclient.Request.Method.PUT;

public class RequestMergeTest {

    @Test
    public void overrideAll() {
        final Request<Object> a = Request.builder()
                .path("/repos/{owner}/{repo}/pulls")
                .path("owner", "tomitribe")
                .path("repo", "orange")
                .header("link", "http://foo.example.com")
                .query("state", "closed")
                .query("head", "cabeza")
                .query("base", "orangebase")
                .query("sort", "long-running")
                .query("direction", "asc")
                .body(new Orange(true))
                .build();
        final Request<Object> b = Request.builder()
                .path("/repos/{owner}/{repo}/issues")
                .path("owner", "apache")
                .path("repo", "red")
                .header("link", "http://bar.example.com")
                .query("state", "open")
                .query("head", "testa")
                .query("base", "redbase")
                .query("sort", "short-running")
                .query("direction", "desc")
                .body(new Orange(false))
                .build();

        Request<Object> c = a.merge(b);

        assertEquals(b.getBody(), c.getBody());
        assertEquals(b.getPath(), c.getPath());
        assertEquals(b.getMethod(), c.getMethod());

        assertMap(b.getHeaderParams(), c.getHeaderParams());
        assertMap(b.getQueryParams(), c.getQueryParams());
        assertMap(b.getPathParams(), c.getPathParams());
    }

    /**
     * Here the Request we are overriding is entirely null values (empty)
     * All the original values should remain
     */
    @Test
    public void overrideNone() {
        final Request<Object> a = Request.builder()
                .path("/repos/{owner}/{repo}/pulls")
                .path("owner", "tomitribe")
                .path("repo", "orange")
                .header("link", "http://foo.example.com")
                .query("state", "closed")
                .query("head", "cabeza")
                .query("base", "orangebase")
                .query("sort", "long-running")
                .query("direction", "asc")
                .body(new Orange(true))
                .build();
        final Request<Object> b = Request.builder()
                .build();

        Request<Object> c = a.merge(b);

        assertEquals(a.getBody(), c.getBody());
        assertEquals(a.getPath(), c.getPath());
        assertEquals(a.getMethod(), c.getMethod());

        assertMap(a.getHeaderParams(), c.getHeaderParams());
        assertMap(a.getQueryParams(), c.getQueryParams());
        assertMap(a.getPathParams(), c.getPathParams());
    }

    /**
     * Here the Request we are overriding is entirely null values (empty)
     * All the original null values should be overwritten
     */
    @Test
    public void overrideEmpty() {
        final Request<Object> a = Request.builder()
                .build();
        final Request<Object> b = Request.builder()
                .path("/repos/{owner}/{repo}/issues")
                .path("owner", "apache")
                .path("repo", "red")
                .header("link", "http://bar.example.com")
                .query("state", "open")
                .query("head", "testa")
                .query("base", "redbase")
                .query("sort", "short-running")
                .query("direction", "desc")
                .body(new Orange(false))
                .build();

        Request<Object> c = a.merge(b);

        assertEquals(b.getBody(), c.getBody());
        assertEquals(b.getPath(), c.getPath());
        assertEquals(b.getMethod(), c.getMethod());

        assertMap(b.getHeaderParams(), c.getHeaderParams());
        assertMap(b.getQueryParams(), c.getQueryParams());
        assertMap(b.getPathParams(), c.getPathParams());
    }

    @Test
    public void mixed() {
        final Request<Object> a = Request.builder()
                .path("/repos/{owner}/{repo}/issues")
                .path("owner", "apache")
                .header("link", "http://foo.example.com")
                .query("state", "closed")
                .query("head", "cabeza")
                .body(new Orange(true))
                .build();
        final Request<Object> b = Request.builder()
                .method(PUT)
                .path("repo", "red")
                .header("next", "http://bar.example.com")
                .query("base", "redbase")
                .query("sort", "short-running")
                .build();

        Request<Object> c = a.merge(b);

        assertEquals(a.getPath(), c.getPath());
        assertEquals(a.getBody(), c.getBody());
        assertEquals(b.getMethod(), c.getMethod());

        assertHeader(c, "link", "http://foo.example.com");
        assertHeader(c, "next", "http://bar.example.com");
        assertHeader(c, "content-type", "application/json");
        assertEquals(3, c.getHeaderParams().size());

        assertPath(c, "owner", "apache");
        assertPath(c, "repo", "red");
        assertEquals(2, c.getPathParams().size());

        assertQuery(c, "state", "closed");
        assertQuery(c, "head", "cabeza");
        assertQuery(c, "base", "redbase");
        assertQuery(c, "sort", "short-running");
        assertEquals(4, c.getQueryParams().size());
    }

    private void assertHeader(final Request<Object> c, final String link, final String expected) {
        assertEquals(expected, c.getHeaderParams().get(link));
    }

    private void assertPath(final Request<Object> c, final String link, final String expected) {
        assertEquals(expected, c.getPathParams().get(link));
    }

    private void assertQuery(final Request<Object> c, final String link, final String expected) {
        assertEquals(expected, c.getQueryParams().get(link));
    }

    private void assertMap(final Map<String, Object> expectedMap, final Map<String, Object> actualMap) {
        for (final Map.Entry<String, Object> entry : actualMap.entrySet()) {
            final Object expected = expectedMap.get(entry.getKey());
            final Object actual = entry.getValue();
            assertEquals(expected, actual);
        }
        assertEquals(expectedMap.size(), expectedMap.size());
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Orange {

        @JsonbProperty("draft")
        private Boolean draft;


        public enum State {
            open, closed, all;
        }

        public enum Sort {
            created("created"),
            updated("updated"),
            popularity("popularity"),
            long_running("long-running");

            private final String name;

            Sort(final String name) {
                this.name = name;
            }

            @Override
            public String toString() {
                return name;
            }
        }

        public enum Direction {
            asc, desc
        }
    }

}
