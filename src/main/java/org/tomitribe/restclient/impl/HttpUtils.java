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
package org.tomitribe.restclient.impl;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HttpUtils {
    private static final String QUERY_RESERVED_CHARACTERS = "?/,";
    // there are more of such characters, ex, '*' but '*' is not affected by UrlEncode
    private static final String PATH_RESERVED_CHARACTERS = "=@/:!$&\'(),;~";
    private static final Predicate<String> NOT_EMPTY = (String s) -> !s.isEmpty();

    private HttpUtils() {
    }

    public static List<PathSegment> getPathSegments(String thePath, boolean decode) {
        return getPathSegments(thePath, decode, true);
    }

    /**
     * Parses path segments taking into account the URI templates and template regexes. Per RFC-3986,
     * "A path consists of a sequence of path segments separated by a slash ("/") character.", however
     * it is possible to include slash ("/") inside template regex, for example "/my/path/{a:b/c}", see
     * please {@link URITemplate}. In this case, the whole template definition is extracted as a path
     * segment, without breaking it.
     * @param thePath path
     * @param decode should the path segments be decoded or not
     * @param ignoreLastSlash should the last slash be ignored or not
     */
    public static List<PathSegment> getPathSegments(String thePath, boolean decode,
                                                    boolean ignoreLastSlash) {

        final List<PathSegment> segments = new ArrayList<>();
        int templateDepth = 0;
        int start = 0;
        for (int i = 0; i < thePath.length(); ++i) {
            if (thePath.charAt(i) == '/') {
                // The '/' is in template (possibly, with arbitrary regex) definition
                if (templateDepth != 0) {
                    continue;
                } else if (start != i) {
                    final String segment = thePath.substring(start, i);
                    segments.add(new PathSegmentImpl(segment, decode));
                }

                // advance the positions, empty path segments
                start = i + 1;
            } else if (thePath.charAt(i) == '{') {
                ++templateDepth;
            } else if (thePath.charAt(i) == '}') {
                --templateDepth; // could go negative, since the template could be unbalanced
            }
        }

        // the URI has unbalanced curly braces, backtrack to the last seen position of the path
        // segment separator and just split segments as-is from there
        if (templateDepth != 0) {
            segments.addAll(
                Arrays
                    .stream(thePath.substring(start).split("/"))
                    .filter(notEmpty())
                    .map(p -> new PathSegmentImpl(p, decode))
                    .collect(Collectors.toList()));

            int len = thePath.length();
            if (len > 0 && thePath.charAt(len - 1) == '/') {
                String value = ignoreLastSlash ? "" : "/";
                segments.add(new PathSegmentImpl(value, false));
            }
        } else {
            // the last symbol is slash
            if (start == thePath.length() && start > 0 && thePath.charAt(start - 1) == '/') {
                String value = ignoreLastSlash ? "" : "/";
                segments.add(new PathSegmentImpl(value, false));
            } else if (!thePath.isEmpty()) {
                final String segment = thePath.substring(start);
                segments.add(new PathSegmentImpl(segment, decode));
            }
        }

        return segments;
    }

    /**
     * Retrieve map of query parameters from the passed in message
     * @return a Map of query parameters.
     */
    public static MultivaluedMap<String, String> getStructuredParams(String query,
                                                                     String sep,
                                                                     boolean decode,
                                                                     boolean decodePlus) {
        MultivaluedMap<String, String> map =
            new MetadataMap<>(new LinkedHashMap<String, List<String>>());

        getStructuredParams(map, query, sep, decode, decodePlus);

        return map;
    }

    public static void getStructuredParams(MultivaluedMap<String, String> queries,
                                           String query,
                                           String sep,
                                           boolean decode,
                                           boolean decodePlus) {
        getStructuredParams(queries, query, sep, decode, decodePlus, false);
    }

    public static void getStructuredParams(MultivaluedMap<String, String> queries,
                                           String query,
                                           String sep,
                                           boolean decode,
                                           boolean decodePlus,
                                           boolean valueIsCollection) {
        if (!isEmpty(query)) {
            for (String part : query.split(sep)) { // fastpath expected
                int index = part.indexOf('=');
                final String name;
                String value = null;
                if (index == -1) {
                    name = part;
                } else {
                    name = part.substring(0, index);
                    value = index < part.length() ? part.substring(index + 1) : "";
                }
                if (valueIsCollection) {
                    if (value != null) {
                        for (String s : value.split(",")) {
                            addStructuredPartToMap(queries, sep, name, s, decode, decodePlus);
                        }
                    }
                } else {
                    addStructuredPartToMap(queries, sep, name, value, decode, decodePlus);
                }
            }
        }
    }

    private static void addStructuredPartToMap(MultivaluedMap<String, String> queries,
                                               String sep,
                                               String name,
                                               String value,
                                               boolean decode,
                                               boolean decodePlus) {

        if (value != null) {
            if (decodePlus && value.contains("+")) {
                value = value.replace('+', ' ');
            }
            if (decode) {
                value = (";".equals(sep))
                    ? pathDecode(value) : UrlUtils.urlDecode(value);
            }
        }

        if (decode) {
            queries.add(UrlUtils.urlDecode(name), value);
        } else {
            queries.add(name, value);
        }
    }

    private static final Pattern ENCODE_PATTERN = Pattern.compile("%[0-9a-fA-F][0-9a-fA-F]");

    /**
     * Encodes partially encoded string. Encode all values but those matching pattern
     * "percent char followed by two hexadecimal digits".
     *
     * @param encoded fully or partially encoded string.
     * @return fully encoded string
     */
    public static String encodePartiallyEncoded(String encoded, boolean query) {
        if (encoded.length() == 0) {
            return encoded;
        }
        Matcher m = ENCODE_PATTERN.matcher(encoded);

        if (!m.find()) {
            return query ? queryEncode(encoded) : pathEncode(encoded);
        }

        int length = encoded.length();
        StringBuilder sb = new StringBuilder(length + 8);
        int i = 0;
        do {
            String before = encoded.substring(i, m.start());
            sb.append(query ? queryEncode(before) : pathEncode(before));
            sb.append(m.group());
            i = m.end();
        } while (m.find());
        String tail = encoded.substring(i, length);
        sb.append(query ? queryEncode(tail) : pathEncode(tail));
        return sb.toString();
    }

    public static String componentEncode(String reservedChars, String value) {

        StringBuilder buffer = null;
        int length = value.length();
        int startingIndex = 0;
        for (int i = 0; i < length; i++) {
            char currentChar = value.charAt(i);
            if (reservedChars.indexOf(currentChar) != -1) {
                if (buffer == null) {
                    buffer = new StringBuilder(length + 8);
                }
                // If it is going to be an empty string nothing to encode.
                if (i != startingIndex) {
                    buffer.append(urlEncode(value.substring(startingIndex, i)));
                }
                buffer.append(currentChar);
                startingIndex = i + 1;
            }
        }

        if (buffer == null) {
            return urlEncode(value);
        }
        if (startingIndex < length) {
            buffer.append(urlEncode(value.substring(startingIndex, length)));
        }

        return buffer.toString();
    }

    public static String queryEncode(String value) {

        return componentEncode(QUERY_RESERVED_CHARACTERS, value);
    }

    public static String urlEncode(String value) {

        return urlEncode(value, StandardCharsets.UTF_8.name());
    }

    public static String urlEncode(String value, String enc) {

        try {
            return URLEncoder.encode(value, enc);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String pathEncode(String value) {

        String result = componentEncode(PATH_RESERVED_CHARACTERS, value);
        // URLEncoder will encode '+' to %2B but will turn ' ' into '+'
        // We need to retain '+' and encode ' ' as %20
        if (result.indexOf('+') != -1) {
            result = result.replace("+", "%20");
        }
        if (result.indexOf("%2B") != -1) {
            result = result.replace("%2B", "+");
        }

        return result;
    }

    public static MultivaluedMap<String, String> getMatrixParams(String path, boolean decode) {
        int index = path.indexOf(';');
        return index == -1 ? new MetadataMap<String, String>()
                           : getStructuredParams(path.substring(index + 1), ";", decode, false);
    }

    public static String pathDecode(String value) {
        return UrlUtils.pathDecode(value);
    }

    public static String fromPathSegment(PathSegment ps) {
        if (PathSegmentImpl.class.isAssignableFrom(ps.getClass())) {
            return ((PathSegmentImpl)ps).getOriginalPath();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ps.getPath());
        for (Map.Entry<String, List<String>> entry : ps.getMatrixParameters().entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(';').append(entry.getKey());
                if (value != null) {
                    sb.append('=').append(value);
                }
            }
        }
        return sb.toString();
    }

    public static Predicate<String> notEmpty() {
        return NOT_EMPTY;
    }

    public static boolean isEmpty(String str) {
        if (str != null) {
            int len = str.length();
            for (int x = 0; x < len; ++x) {
                if (str.charAt(x) > ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public static byte[] toBytes(String str, String enc) {
        try {
            return str.getBytes(enc);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
