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
package io.github.jwdeveloper.tiktok.data.models;

import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveException;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class Picture {

    @Getter
    private final String link;

    private Image image;
    private static final OkHttpClient httpClient = new OkHttpClient();

    public Picture(String link) {
        this.link = link;
    }

    public static Picture map(io.github.jwdeveloper.tiktok.messages.data.Image profilePicture) {
        var index = profilePicture.getUrlCount() - 1;
        if (index < 0) {
            return new Picture("");
        }
        var url = profilePicture.getUrl(index);
        return new Picture(url);
    }

    public boolean isDownloaded() {
        return image != null;
    }

    public Image downloadImage() {
        if (isDownloaded()) {
            return image;
        }
        image = download(link);
        return image;
    }

    public CompletableFuture<Image> downloadImageAsync() {
        return CompletableFuture.supplyAsync(this::downloadImage);
    }

    private BufferedImage download(String urlString) {
        if (urlString.isEmpty()) {
            return null;
        }

        Request request = new Request.Builder()
                .url(urlString)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new TikTokLiveException("Failed to download image: HTTP " + response.code());
            }

            if (response.body() == null) {
                throw new TikTokLiveException("Response body is null");
            }

            byte[] imageBytes = response.body().bytes();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
                return ImageIO.read(bais);
            }
        } catch (IOException e) {
            throw new TikTokLiveException("Unable to download or parse image", e);
        }
    }

    public static Picture empty() {
        return new Picture("");
    }

    public Picture asUnsigned() {
        if (link == null || link.isEmpty()) {
            return this;
        }
        // p16-sign-va.tiktokcdn.com -> p16-va.tiktokcdn.com || p16-sign.tiktokcdn.com -> p16.tiktokcdn.com
        return new Picture(link.replace("-sign-", "-").replace("-sign.", "."));
    }

    @Override
    public String toString() {
        return "Picture{link='" + link + "', image=" + image + "}";
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Picture) {
            Picture picture = (Picture) o;
            return picture.link != null && picture.link.equals(link);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(link);
    }
}