package com.maimai.integration.logistics;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class Kuaidi100TransportTest {
    @Test void writesExactFormAndClosesResources() throws Exception {
        var connection = new Stub(200, "{\"status\":\"200\"}".getBytes(StandardCharsets.UTF_8));
        var result = transport(connection).post("customer=fixture&sign=ABC&param=%7B%7D");
        assertThat(result).isEqualTo("{\"status\":\"200\"}");
        assertThat(connection.sent.toString(StandardCharsets.UTF_8)).isEqualTo("customer=fixture&sign=ABC&param=%7B%7D");
        assertThat(connection.getRequestMethod()).isEqualTo("POST");
        assertThat(connection.getConnectTimeout()).isEqualTo(3000);
        assertThat(connection.getReadTimeout()).isBetween(1, 8000);
        assertThat(connection.getInstanceFollowRedirects()).isFalse();
        assertThat(connection.inputClosed).isTrue();
        assertThat(connection.disconnected).isTrue();
    }

    @Test void rejectsRedirectAndHttpErrorsWithoutReadingBody() {
        for (int status : new int[]{302, 403, 500}) {
            var connection = new Stub(status, "untrusted body".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> transport(connection).post("test=1")).isInstanceOf(IOException.class);
            assertThat(connection.bodyOpened).isFalse();
            assertThat(connection.disconnected).isTrue();
        }
    }

    @Test void rejectsOversizedPayloadAndClosesStream() {
        var connection = new Stub(200, new byte[1024 * 1024 + 1]);
        assertThatThrownBy(() -> transport(connection).post("test=1")).isInstanceOf(IOException.class);
        assertThat(connection.inputClosed).isTrue();
        assertThat(connection.disconnected).isTrue();
    }

    private Kuaidi100Transport transport(Stub connection) {
        return new Kuaidi100Transport.Default(new Kuaidi100ConnectionFactory() {
            @Override public HttpURLConnection open() {return connection;}
        });
    }

    private static class Stub extends HttpURLConnection {
        final int status;
        final byte[] body;
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        boolean disconnected, inputClosed, bodyOpened;
        Stub(int status, byte[] body) {
            super(url()); this.status = status; this.body = body;
        }
        private static URL url() {
            try {return URI.create("https://poll.kuaidi100.com/poll/query.do").toURL();}
            catch (MalformedURLException e) {throw new AssertionError(e);}
        }
        @Override public OutputStream getOutputStream() {return sent;}
        @Override public int getResponseCode() {return status;}
        @Override public InputStream getInputStream() {
            bodyOpened = true;
            return new ByteArrayInputStream(body) {
                @Override public void close() {inputClosed = true;}
            };
        }
        @Override public void disconnect() {disconnected = true;}
        @Override public boolean usingProxy() {return false;}
        @Override public void connect() {}
    }
}
