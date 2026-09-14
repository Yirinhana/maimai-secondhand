package com.maimai.integration.logistics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Pluggable transport contract for Kuaidi100 polling.
 *
 * <p>The interface exists so that the client can be unit tested with a deterministic
 * in-memory implementation while production code uses a real
 * {@link HttpURLConnection} driven by {@link Kuaidi100ConnectionFactory}.
 *
 * <p>Implementations MUST honour the following contract:
 * <ul>
 *     <li>Strict 3 second connect timeout, 8 second read timeout, never follow redirects.</li>
 *     <li>The response body is capped at 1 MiB; any excess MUST cause an {@link IOException}.</li>
 *     <li>The total wall clock budget for the call is 11 seconds including connect.</li>
 *     <li>Connections are closed in a {@code finally} block by the implementation.</li>
 *     <li>Non 2xx responses are reported as failures; the body is never trusted.</li>
 * </ul>
 */
public interface Kuaidi100Transport {

    /**
     * Execute a single POST request to the fixed Kuaidi100 endpoint.
     *
     * @param formBody application/x-www-form-urlencoded body to send verbatim
     * @return the raw response body (decoded as UTF-8)
     * @throws IOException on any transport level failure (timeout, oversized payload, non 2xx, ...)
     */
    String post(String formBody) throws IOException;

    /** Adapter that uses a {@link Kuaidi100ConnectionFactory}. */
    @org.springframework.stereotype.Component
    final class Default implements Kuaidi100Transport {

        private static final int CONNECT_TIMEOUT_MS = 3_000;
        private static final int READ_TIMEOUT_MS = 8_000;
        private static final long MAX_RESPONSE_BYTES = 1L * 1024L * 1024L;

        private final Kuaidi100ConnectionFactory factory;

        public Default(Kuaidi100ConnectionFactory factory) {
            this.factory = factory;
        }

        @Override
        public String post(String formBody) throws IOException {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(11);
            HttpURLConnection conn = factory.open();
            try {
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setFixedLengthStreamingMode(formBody.getBytes(StandardCharsets.UTF_8).length);

                byte[] payload = formBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                    os.flush();
                }

                conn.setReadTimeout(remainingTimeout(deadline));
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("Kuaidi100 HTTP " + status);
                }
                try (InputStream stream = conn.getInputStream()) {
                    return readCapped(stream, conn, deadline);
                }
            } finally {
                conn.disconnect();
            }
        }

        private static int remainingTimeout(long deadline) throws IOException {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.net.SocketTimeoutException("Logistics request timed out");
            return (int) Math.max(1, Math.min(READ_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining)));
        }

        private static String readCapped(InputStream stream, HttpURLConnection conn, long deadline) throws IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while (true) {
                conn.setReadTimeout(remainingTimeout(deadline));
                n = stream.read(buf);
                if (n == -1) break;
                total += n;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Kuaidi100 response exceeded 1 MiB cap");
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
