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
package io.github.jwdeveloper.tiktok.http;

import io.github.jwdeveloper.tiktok.common.ActionResult;
import io.github.jwdeveloper.tiktok.data.settings.HttpClientSettings;
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveRequestException;
import lombok.AllArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@AllArgsConstructor
public class HttpClient {

    protected final HttpClientSettings httpClientSettings;
    protected final String url;
    protected final RequestBody bodyPublisher;

    public ActionResult<Response> toHttpResponse() {
        OkHttpClient client = prepareClient();
        Request request = prepareRequest();

        try {
            Response response = client.newCall(request).execute();
            var result = ActionResult.of(response);

            return switch (response.code()) {
                case 420 -> result.message("HttpResponse Code:", response.code(), "| IP Cloudflare Blocked.").failure();
                case 429 -> {
                    String wait = response.header("ratelimit-reset");
                    if (wait == null) {
                        yield result.message("HttpResponse Code:", response.code(), "| Sign server rate limit reached. Try again later.").failure();
                    }
                    Duration duration = Duration.ofSeconds(Long.parseLong(wait));
                    yield result.message("HttpResponse Code:", response.code(),
                            String.format("| Sign server rate limit reached. Try again in %02d:%02d.",
                                    duration.toMinutesPart(), duration.toSecondsPart())).failure();
                }
                case 500, 501, 502, 503 -> result.message("HttpResponse Code:", response.code(), "| Sign server Error. Try again later.").failure();
                case 504 -> result.message("HttpResponse Code:", response.code(), "| Sign server Timeout. Try again later.").failure();
                case 200 -> result.success();
                default -> result.message("HttpResponse Code:", response.code()).failure();
            };
        } catch (IOException e) {
            throw new TikTokLiveRequestException(e);
        }
    }

    public ActionResult<String> toJsonResponse() {
        ActionResult<Response> httpResult = toHttpResponse();
        if (httpResult.isFailure()) {
            return httpResult.cast();
        }

        try (Response response = httpResult.getContent()) {
            ResponseBody body = response.body();
            if (body == null) {
                return ActionResult.failure("Response body is null");
            }
            String content = body.string();
            return ActionResult.success(content);
        } catch (IOException e) {
            throw new TikTokLiveRequestException(e);
        }
    }

    public ActionResult<byte[]> toBinaryResponse() {
        ActionResult<Response> httpResult = toHttpResponse();
        if (httpResult.isFailure()) {
            return httpResult.cast();
        }

        try (Response response = httpResult.getContent()) {
            ResponseBody body = response.body();
            if (body == null) {
                return ActionResult.failure("Response body is null");
            }
            byte[] content = body.bytes();
            return ActionResult.success(content);
        } catch (IOException e) {
            throw new TikTokLiveRequestException(e);
        }
    }

    public URI toUri() {
        String stringUrl = prepareUrlWithParameters(url, httpClientSettings.getParams());
        return URI.create(stringUrl);
    }

    protected Request prepareRequest() {
        Request.Builder requestBuilder = new Request.Builder();

        // Set URL
        requestBuilder.url(toUri().toString());

        // Set method (GET or POST)
        if (bodyPublisher != null) {
            requestBuilder.post(bodyPublisher);
        } else {
            requestBuilder.get();
        }

        // Add cookies to headers
        if (!httpClientSettings.getCookies().isEmpty()) {
            String cookieString = httpClientSettings.getCookies().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));
            httpClientSettings.getHeaders().put("Cookie", cookieString);
        }

        // Add headers
        httpClientSettings.getHeaders().forEach(requestBuilder::addHeader);

        return requestBuilder.build();
    }

    protected OkHttpClient prepareClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Set timeouts
        Duration timeout = httpClientSettings.getTimeout();
        builder.connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        builder.readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        builder.writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

        // Follow redirects
        builder.followRedirects(true);
        builder.followSslRedirects(true);

        // Cookie jar
        builder.cookieJar(new CookieJar() {
            private final java.util.HashMap<String, java.util.List<Cookie>> cookieStore = new java.util.HashMap<>();

            @Override
            public void saveFromResponse(HttpUrl url, java.util.List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public java.util.List<Cookie> loadForRequest(HttpUrl url) {
                java.util.List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new java.util.ArrayList<>();
            }
        });

        return builder.build();
    }

    protected String prepareUrlWithParameters(String url, Map<String, Object> parameters) {
        if (parameters.isEmpty()) {
            return url;
        }

        return url + "?" + parameters.entrySet().stream().map(entry -> {
            String encodedKey = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String encodedValue = URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8);
            return encodedKey + "=" + encodedValue;
        }).collect(Collectors.joining("&"));
    }
}