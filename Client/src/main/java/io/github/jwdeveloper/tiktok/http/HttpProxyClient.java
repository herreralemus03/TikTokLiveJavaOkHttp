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
import io.github.jwdeveloper.tiktok.data.settings.ProxyClientSettings;
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveRequestException;
import io.github.jwdeveloper.tiktok.exceptions.TikTokProxyRequestException;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class HttpProxyClient extends HttpClient {

	private final ProxyClientSettings proxySettings;

	public HttpProxyClient(HttpClientSettings httpClientSettings, String url, RequestBody bodyPublisher) {
		super(httpClientSettings, url, bodyPublisher);
		this.proxySettings = httpClientSettings.getProxyClientSettings();
	}

	@Override
	public ActionResult<Response> toHttpResponse() {
		return switch (proxySettings.getType()) {
			case HTTP, SOCKS -> handleProxyRequest();
			default -> super.toHttpResponse();
		};
	}

	public ActionResult<Response> handleProxyRequest() {
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

		while (proxySettings.hasNext()) {
			try {
				InetSocketAddress address = proxySettings.next().toSocketAddress();
				Proxy proxy = new Proxy(proxySettings.getType(), address);
				builder.proxy(proxy);

				OkHttpClient client = builder.build();
				Request request = prepareRequest();

				Response response = client.newCall(request).execute();

				if (response.code() != 200) {
					response.close();
					continue;
				}

				return ActionResult.success(response);

			} catch (IOException e) {
				if (e.getMessage() != null && e.getMessage().contains("503") && proxySettings.isFallback()) {
					// Indicates proxy protocol is not supported
					return super.toHttpResponse();
				}

				if (proxySettings.isAutoDiscard()) {
					proxySettings.remove();
				}

				// Continue to next proxy
				if (!proxySettings.hasNext()) {
					throw new TikTokProxyRequestException(e);
				}
			} catch (Exception e) {
				throw new TikTokLiveRequestException(e);
			}
		}

		throw new TikTokLiveRequestException("No more proxies available!");
	}
}