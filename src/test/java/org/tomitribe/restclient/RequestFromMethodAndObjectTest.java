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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tomitribe.util.Join;

import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbTransient;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestFromMethodAndObjectTest {


    private Request<?> request;

    @BeforeEach
    public void before() {

        final OrangeClient orangeClient = (OrangeClient) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{OrangeClient.class}, (proxy, method, args) -> {
                    request = Request.from(method, args);
                    return null;
                }
        );

        final Orange build = Orange.builder()
                .base("orange")
                .direction(Orange.Direction.desc)
                .head("cabeza")
                .owner("apache")
                .repo("orange")
                .sort(Orange.Sort.popularity)
                .state(Orange.State.closed)
                .link("http://bar.example.com/")
                .draft(true)
                .build();

        orangeClient.orange("red",
                "orangebase",
                Orange.Sort.long_running,
                Orange.Direction.asc,
                "http://foo.example.com/",
                build);
    }

    @Test
    public void getPath() {
        assertEquals("/repos/{owner}/{repo}/pulls", request.getPath());
    }

    @Test
    public void getURI() {
        final String expected = "/repos/apache/red/pulls?head=cabeza&state=closed&sort=long-running&base=orangebase&direction=asc";
        final String actual = request.getURI().toASCIIString();

        Function<String, String> normalize = s -> {
            final String[] parts = s.split("[?&]");
            Arrays.sort(parts);
            return Join.join("\n", (Object[]) parts);
        };

        assertEquals(normalize.apply(expected), normalize.apply(actual));
    }

    @Test
    public void getQueryParams() {
        final String actual = request.getQueryParams().entrySet().stream()
                .map(entry -> String.format("Query{name='%s', value='%s'}", entry.getKey(), entry.getValue()))
                .sorted()
                .reduce((s, s2) -> s + "\n" + s2)
                .get();

        assertEquals("Query{name='base', value='orangebase'}\n" +
                "Query{name='direction', value='asc'}\n" +
                "Query{name='head', value='cabeza'}\n" +
                "Query{name='sort', value='long-running'}\n" +
                "Query{name='state', value='closed'}", actual);
    }

    @Test
    public void getHeaderParams() {
        final String actual = request.getHeaderParams().entrySet().stream()
                .map(entry -> String.format("Header{name='%s', value='%s'}", entry.getKey(), entry.getValue()))
                .reduce((s, s2) -> s + "\n" + s2)
                .get();

        assertEquals("Header{name='link', value='http://foo.example.com/'}", actual);
    }


    @Test
    public void getMethod() {
        assertEquals(Request.Method.GET, request.getMethod());
    }

    @Test
    public void getPathParams() {
        final String actual = request.getPathParams().entrySet().stream()
                .map(entry -> String.format("Path{name='%s', value='%s'}", entry.getKey(), entry.getValue()))
                .reduce((s, s2) -> s + "\n" + s2)
                .get();

        assertEquals("Path{name='owner', value='apache'}\n" +
                "Path{name='repo', value='red'}", actual);
    }

    @Test
    public void getBody() {

        assertEquals("{\n" +
                "  \"draft\":true\n" +
                "}", request.getBody());
    }

    public interface OrangeClient {
        @GET
        @Path("/repos/{owner}/{repo}/pulls")
        void orange(
                @PathParam("repo") final String repo,
                @QueryParam("base") final String base,
                @QueryParam("sort") final Orange.Sort sort,
                @QueryParam("direction") final Orange.Direction direction,
                @HeaderParam("link") final String link,
                Orange orange
        );
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Orange {

        @JsonbTransient
        @PathParam("owner")
        private String owner;

        @JsonbTransient
        @PathParam("repo")
        private String repo;

        @JsonbTransient
        @QueryParam("state")
        private Orange.State state;

        @JsonbTransient
        @QueryParam("head")
        private String head;

        @JsonbTransient
        @QueryParam("base")
        private String base;

        @JsonbTransient
        @QueryParam("sort")
        private Orange.Sort sort;

        @JsonbTransient
        @QueryParam("direction")
        private Orange.Direction direction;

        @JsonbTransient
        @HeaderParam("link")
        private String link;

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