/*
 * Copyright (c) 2023-2024 jwdeveloper jacekwoln@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.jwdeveloper.tiktok.data.models.http;

import lombok.Data;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URI;
import java.util.*;

@Data
public class HttpData {
    String url;
    String method;
    Map<String, List<String>> headers = new TreeMap<>();
    Map<String, String> parameters = new TreeMap<>();
    int status;
    String body = "";

    public static HttpData map(Request request) {
        var data = new HttpData();
        data.setUrl(request.url().uri().getPath());
        data.setMethod(request.method());
        data.setParameters(extractQueryParams(request.url().uri()));
        data.setStatus(200);

        if (request.body() != null) {
            data.setBody(request.body().toString());
        }

        // Convert OkHttp headers to Map
        Map<String, List<String>> headersMap = new TreeMap<>();
        for (String name : request.headers().names()) {
            headersMap.put(name, request.headers().values(name));
        }
        data.setHeaders(Collections.unmodifiableMap(headersMap));

        return data;
    }

    public static HttpData map(Response response) {
        var data = new HttpData();
        data.setUrl(response.request().url().uri().getPath());
        data.setMethod(response.request().method());
        data.setParameters(extractQueryParams(response.request().url().uri()));
        data.setStatus(response.code());

        try {
            if (response.body() != null) {
                data.setBody(response.body().string());
            }
        } catch (Exception e) {
            data.setBody("");
        }

        // Convert OkHttp headers to Map
        Map<String, List<String>> headersMap = new TreeMap<>();
        for (String name : response.headers().names()) {
            headersMap.put(name, response.headers().values(name));
        }
        data.setHeaders(Collections.unmodifiableMap(headersMap));

        return data;
    }

    private static Map<String, String> extractQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();

        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length > 1) {
                    params.put(keyValue[0], keyValue[1]);
                } else {
                    params.put(keyValue[0], ""); // Empty value for parameter without explicit value
                }
            }
        }
        return params;
    }
}