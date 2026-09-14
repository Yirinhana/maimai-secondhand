package com.maimai.integration.logistics;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal factory abstraction for creating {@link HttpURLConnection} instances targeted at
 * the fixed Kuaidi100 polling endpoint.
 *
 * <p>The factory keeps the host and path hard-coded and ignores any caller supplied URL.
 * This guarantees we cannot be redirected to an arbitrary host via configuration or
 * runtime data and also allows tests to substitute a controllable transport without
 * having to depend on a third-party HTTP library.
 *
 * <p>Callers receive an unconnected {@link HttpURLConnection}; timeouts, redirect policy
 * and headers must be configured by the client itself so that production and test
 * implementations stay consistent.
 */
@org.springframework.stereotype.Component
public class Kuaidi100ConnectionFactory {

    public static final String HOST = "https://poll.kuaidi100.com";
    public static final String PATH = "/poll/query.do";

    public HttpURLConnection open() throws IOException {
        return (HttpURLConnection) new URL(HOST + PATH).openConnection();
    }

}
