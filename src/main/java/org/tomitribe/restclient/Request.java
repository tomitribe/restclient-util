/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tomitribe.restclient;

import org.tomitribe.restclient.impl.UriBuilderImpl;
import org.tomitribe.util.Join;

import javax.json.bind.Jsonb;
import javax.json.bind.annotation.JsonbProperty;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Request<ResponseType> {


    private final Method method;
    private final String path;
    private final String body;
    private final Class<ResponseType> responseType;
    private final Map<String, Object> pathParams;
    private final Map<String, Object> queryParams;
    private final Map<String, Object> headerParams;

    public Request(final Method method, final String path, final String body,
                   final Map<String, Object> queryParams, final Map<String, Object> headerParams,
                   final Map<String, Object> pathParams, final Class<ResponseType> responseType) {
        this.method = method;
        this.path = path;
        this.body = body;
        this.responseType = responseType;
        this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
        this.pathParams = Collections.unmodifiableMap(new LinkedHashMap<>(pathParams));

        // Lower case all headers
        final LinkedHashMap<String, Object> headers = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : headerParams.entrySet()) {
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        this.headerParams = Collections.unmodifiableMap(headers);

    }

    public static <ResponseType> Builder<ResponseType> builder() {
        return new Builder<>();
    }

    public Request<ResponseType> path(final String name, final Object value) {
        this.pathParams.put(name, value);
        return this;
    }

    public Request<ResponseType> query(final String name, final Object value) {
        this.queryParams.put(name, value);
        return this;
    }

    public Request<ResponseType> header(final String name, final Object value) {
        this.headerParams.put(name, value);
        return this;
    }

    public Request<ResponseType> body(final Object value) {
        final String json = JsonMarshalling.toFormattedJson(value);
        return toBuilder().body(json)
                .header("content-type", "application/json")
                .build();
    }

    public <T> Request<T> response(final Class<T> responseType) {
        return new Builder<T>()
                .method(this.method)
                .path(this.path)
                .body(this.body)
                .pathParams(this.pathParams)
                .queryParams(this.queryParams)
                .headerParams(this.headerParams)
                .responseType(responseType).build();
    }

    public String getPath() {
        return path;
    }

    public Method getMethod() {
        return method;
    }

    public Class<ResponseType> getResponseType() {
        return responseType;
    }

    public Map<String, Object> getPathParams() {
        return pathParams;
    }

    public boolean hasBody() {
        return body != null;
    }

    public URI getURI() {
        return toUriBuilder().resolveTemplates(pathParams).build();
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public Map<String, Object> getHeaderParams() {
        return headerParams;
    }

    public String getBody() {
        return body;
    }

    public Entity<String> getEntity() {
        return hasBody() ? Entity.entity(this.getBody(), MediaType.APPLICATION_JSON_TYPE) : null;
    }

    public UriBuilder toUriBuilder() {
        final UriBuilder builder = new UriBuilderImpl().path(path);

        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    public static Request<?> target(final String path, final Object... pathParameters) {
        final Template template = new Template(path);
        final HashMap<String, Object> pathParams = new HashMap<>();

        final List<String> variables = template.getVariables();
        final ListIterator<String> iterator = variables.listIterator();
        for (int i = 0; i < pathParameters.length; i++) {
            if (!iterator.hasNext()) {
                final String message = String.format("Excess path parameters supplied.  Path %s contains %s parameters, but %s were supplied. ",
                        path, variables.size(), pathParameters.length);
                throw new IllegalStateException(message);
            }
            final String name = iterator.next();
            final Object parameter = pathParameters[i];
            pathParams.put(name, parameter);
        }

        return new Request<>(null, path, null, new HashMap<>(), new HashMap<>(), pathParams, null);
    }

    public static Request<?> from(final String pathTemplate, final Object annotatedObject) {
        final Map<AnnotatedField.Type, List<AnnotatedField>> fields = Stream.of(annotatedObject.getClass().getDeclaredFields())
                .map(field -> AnnotatedField.from(field, annotatedObject))
                .collect(Collectors.groupingBy(AnnotatedField::getType));

        final Map<String, Object> queryParams = mapToObject(fields, AnnotatedField.Type.QUERY);
        final Map<String, Object> headerParams = mapToObject(fields, AnnotatedField.Type.HEADER);
        final Map<String, Object> pathParams = mapToObject(fields, AnnotatedField.Type.PATH);

        final String json = fields.get(AnnotatedField.Type.BODY) == null ? null : toJson(annotatedObject);

        return new Request<>(null, pathTemplate, json, queryParams, headerParams, pathParams, null);
    }

    public static Request<?> from(final Object annotatedObject) {
        return from(null, annotatedObject);
    }

    public static Request<?> from(final java.lang.reflect.Method method, final Object[] args) {
        final Path pathAnnotation = method.getAnnotation(Path.class);
        final String path = pathAnnotation.value();

        final List<Param<Parameter>> params = Param.from(method, args);

        final List<Object> unknown = params.stream()
                .filter(parameterParam -> parameterParam.getType().equals(Param.Type.UNKNOWN))
                .map(Param::get)
                .collect(Collectors.toList());

        if (unknown.size() > 1) {
            throw new InvalidMethodSignatureException("Client interface methods may only have one non-annotated parameter. Found " + unknown.size(), method);
        }


        final Request<?> requestFromObject;
        if (unknown.size() == 1) {
            final Object annotatedObject = unknown.get(0);
            requestFromObject = from(annotatedObject);
        } else {
            requestFromObject = null;
        }

        final List<Param<Field>> fields = new ArrayList<>();
        final Map<String, Object> pathParams = mapParams(params, fields, Param.Type.PATH);
        final Map<String, Object> queryParams = mapParams(params, fields, Param.Type.QUERY);
        final Map<String, Object> headerParams = mapParams(params, fields, Param.Type.HEADER);

        if (!Void.TYPE.equals(method.getReturnType())) {
            final Set<String> accept = getAccept(headerParams);
            accept.add("application/json");
            headerParams.put("accept", Join.join(", ", accept));
        }


        final Method httpMethod = getRequestMethod(method);
        final Request<Object> requestFromParameters = new Request<>(httpMethod, path, null, queryParams, headerParams, pathParams, null);

        if (requestFromObject == null) return requestFromParameters;
        return requestFromObject.merge(requestFromParameters);
    }

    private static Method getRequestMethod(final java.lang.reflect.Method method) {
        if (method.isAnnotationPresent(GET.class)) return Method.GET;
        if (method.isAnnotationPresent(POST.class)) return Method.POST;
        if (method.isAnnotationPresent(PUT.class)) return Method.PUT;
        if (method.isAnnotationPresent(DELETE.class)) return Method.DELETE;
        if (method.isAnnotationPresent(PATCH.class)) return Method.PATCH;
        if (method.isAnnotationPresent(OPTIONS.class)) return Method.OPTIONS;
        if (method.isAnnotationPresent(HEAD.class)) return Method.HEAD;
        throw new InvalidMethodSignatureException("Method must be annotated with one of @GET, @POST, @PUT, @DELETE, @PATCH, @OPTIONS or @HEAD", method);
    }

    private static Set<String> getAccept(final Map<String, Object> headerParams) {
        final Object accept = headerParams.get("accept");

        if (accept == null) return new HashSet<>();
        final String[] values = (accept + "").split(" *, *");
        return new HashSet<>(Arrays.asList(values));
    }

    private static Map<String, Object> mapParams(final List<Param<Parameter>> params, final List<Param<Field>> fields, final Param.Type path) {
        return Stream.concat(fields.stream(), params.stream())
                .filter(param -> path.equals(param.getType()))
                .filter(param -> param.get() != null)
                .collect(Collectors.toMap(Param::getName, Param::get));
    }

    private static Map<String, Object> mapToObject(final Map<AnnotatedField.Type, List<AnnotatedField>> fields, final AnnotatedField.Type type) {
        final List<AnnotatedField> list = fields.getOrDefault(type, Collections.EMPTY_LIST);

        return list.stream()
                .collect(Collectors.toMap(AnnotatedField::getName, AnnotatedField::getValue));
    }

    private static String toJson(final Object body) {
        final Jsonb jsonb = JsonbInstances.get();
        return jsonb.toJson(body);
    }

    public Builder<ResponseType> toBuilder() {
        return new Builder<ResponseType>()
                .method(this.method)
                .path(this.path)
                .body(this.body)
                .responseType(this.responseType)
                .pathParams(new LinkedHashMap<>(this.pathParams))
                .queryParams(new LinkedHashMap<>(this.queryParams))
                .headerParams(new LinkedHashMap<>(this.headerParams));
    }

    public <T> Request<T> merge(final Request<T> that) {
        // Start with a copy of this request
        final Builder<T> merged = new Builder<T>()
                .method(this.method)
                .path(this.path)
                .body(this.body)
                .pathParams(new LinkedHashMap<>(this.pathParams))
                .queryParams(new LinkedHashMap<>(this.queryParams))
                .headerParams(new LinkedHashMap<>(this.headerParams));

        // Override with the supplied request
        merged.queryParams.putAll(that.queryParams);
        merged.pathParams.putAll(that.pathParams);
        merged.headerParams.putAll(that.headerParams);

        if (that.body != null) merged.body(that.body);
        if (that.path != null) merged.path(that.path);
        if (that.method != null) merged.method(that.method);

        if (that.responseType != null) {
            merged.responseType(that.responseType);
        } else if (this.responseType != null) {
            merged.responseType((Class<T>) this.responseType);
        }

        return merged.build();
    }

    private static class AnnotatedField {
        private final Object object;
        private final Field field;
        private final String name;
        private final Type type;

        public AnnotatedField(final Object object, final Field field, final String name, final Type type) {
            this.object = object;
            this.field = SetAccessible.on(field);
            this.name = name;
            this.type = type;
        }

        public Field getField() {
            return field;
        }

        public Object getValue() {
            try {
                return field.get(object);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot get value of field: " + field.toGenericString(), e);
            }
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public static AnnotatedField from(final Field field, final Object object) {
            if (field.isAnnotationPresent(QueryParam.class)) {
                final QueryParam param = field.getAnnotation(QueryParam.class);
                return new AnnotatedField(object, field, param.value(), Type.QUERY);
            }

            if (field.isAnnotationPresent(PathParam.class)) {
                final PathParam param = field.getAnnotation(PathParam.class);
                return new AnnotatedField(object, field, param.value(), Type.PATH);
            }

            if (field.isAnnotationPresent(HeaderParam.class)) {
                final HeaderParam param = field.getAnnotation(HeaderParam.class);
                return new AnnotatedField(object, field, param.value(), Type.HEADER);
            }

            if (field.isAnnotationPresent(JsonbProperty.class)) {
                final JsonbProperty param = field.getAnnotation(JsonbProperty.class);
                return new AnnotatedField(object, field, param.value(), Type.BODY);
            }

            throw new UnsupportedOperationException("Field must be annotated QueryParam, PathParam, HeaderParam or JsonbProperty: " + field.toGenericString());
        }

        enum Type {
            BODY,
            HEADER,
            PATH,
            QUERY
        }
    }

    public static class SetAccessible<T extends AccessibleObject> implements PrivilegedAction<T> {
        private final T object;

        public SetAccessible(final T object) {
            this.object = object;
        }

        public T run() {
            object.setAccessible(true);
            return object;
        }

        public static <T extends AccessibleObject> T on(final T object) {
            return (T) AccessController.doPrivileged(new SetAccessible<>(object));
        }
    }

    public enum Method {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
    }

    public static class Builder<ResponseType> {
        private Method method;
        private String path;
        private String body;
        private Class<ResponseType> responseType;
        private Map<String, Object> pathParams = new LinkedHashMap<>();
        private Map<String, Object> queryParams = new LinkedHashMap<>();
        private Map<String, Object> headerParams = new LinkedHashMap<>();

        Builder() {
        }

        public Builder<ResponseType> method(Method method) {
            this.method = method;
            return this;
        }

        public Builder<ResponseType> path(String path) {
            this.path = path;
            return this;
        }

        public Builder<ResponseType> body(Object body) {
            final String json = JsonMarshalling.toFormattedJson(body);
            header("content-type", "application/json");
            this.body = json;
            return this;
        }

        public Builder<ResponseType> body(String body) {
            this.body = body;
            return this;
        }

        public Builder<ResponseType> responseType(Class<ResponseType> responseType) {
            this.responseType = responseType;
            return this;
        }

        public Builder<ResponseType> pathParams(Map<String, Object> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        public Builder<ResponseType> queryParams(Map<String, Object> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder<ResponseType> headerParams(Map<String, Object> headerParams) {
            this.headerParams = new LinkedHashMap<>();
            for (final Map.Entry<String, Object> entry : headerParams.entrySet()) {
                this.headerParams.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            return this;
        }

        public Builder<ResponseType> header(final String name, final Object value) {
            headerParams.put(name.toLowerCase(), value);
            return this;
        }

        /**
         * Adds a path parameter to the pathParams map
         * @param name name of the path parameter
         * @param value value of the path parameter
         */
        public Builder<ResponseType> path(final String name, final Object value) {
            pathParams.put(name, value);
            return this;
        }

        public Builder<ResponseType> query(final String name, final Object value) {
            queryParams.put(name, value);
            return this;
        }

        public Request<ResponseType> build() {
            return new Request<>(method, path, body, queryParams, headerParams, pathParams, responseType);
        }

        public String toString() {
            return "Request.Builder(method=" + this.method + ", path=" + this.path + ", body=" + this.body +
                    ", responseType=" + this.responseType + ", pathParams=" + this.pathParams + ", queryParams=" +
                    this.queryParams + ", headerParams=" + this.headerParams + ")";
        }
    }
}
