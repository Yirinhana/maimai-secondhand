package com.maimai.integration.maps;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class HttpUrlConnectionMapsTransportTest {
    @Test
    void transportEncodesValuesAndRestrictsNetworkBehaviour() throws Exception {
        StubConnection stub=new StubConnection(200,"{\"status\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        URL[] requested=new URL[1];
        var transport=new HttpUrlConnectionMapsTransport("test-key",url->{requested[0]=url;return stub;});
        assertThat(transport.get("/v3/geocode/geo",Map.of("address","上海 & A"))).contains("status");
        assertThat(requested[0].getHost()).isEqualTo("restapi.amap.com");
        assertThat(requested[0].getQuery()).contains("%26").doesNotContain("上海");
        assertThat(stub.getInstanceFollowRedirects()).isFalse();
        assertThat(stub.getConnectTimeout()).isEqualTo(3000);
        assertThat(stub.getReadTimeout()).isBetween(1,8000);
        assertThat(stub.disconnected).isTrue();
    }
    @Test
    void httpErrorsAndRedirectsCannotLookLikeSuccessfulMapsData() throws Exception {
        for(int status:new int[]{302,403,500}) {
            StubConnection stub=new StubConnection(status,"{\"status\":\"1\",\"infocode\":\"10000\"}".getBytes());
            var transport=new HttpUrlConnectionMapsTransport("private-test-key",url->stub);
            assertThatThrownBy(()->transport.get("/v3/geocode/geo",Map.of())).isInstanceOf(IOException.class)
                .hasMessageNotContaining("private-test-key");
            assertThat(stub.disconnected).isTrue();
        }
    }
    @Test
    void oversizedResponseIsRejectedInsteadOfSilentlyTruncated() throws Exception {
        StubConnection stub=new StubConnection(200,new byte[1024*1024+1]);
        var transport=new HttpUrlConnectionMapsTransport("private-test-key",url->stub);
        assertThatThrownBy(()->transport.get("/v3/geocode/geo",Map.of())).isInstanceOf(IOException.class);
        assertThat(stub.disconnected).isTrue();
    }
    @Test
    void missingKeyAndUnapprovedPathsDoNotOpenAConnection() {
        var transport=new HttpUrlConnectionMapsTransport("",url->{throw new AssertionError("network must not run");});
        assertThatThrownBy(()->transport.get("/v3/geocode/geo",Map.of())).isInstanceOf(IOException.class);
        var keyed=new HttpUrlConnectionMapsTransport("private-test-key",url->{throw new AssertionError("network must not run");});
        assertThatThrownBy(()->keyed.get("https://example.test",Map.of())).isInstanceOf(IOException.class);
        assertThatThrownBy(()->keyed.get("/v3/geocode/geo",Map.of("key","replace"))).isInstanceOf(IOException.class);
    }
    private static final class StubConnection extends HttpURLConnection {
        private final int status;
        private final byte[] body;
        boolean disconnected;
        StubConnection(int status,byte[] body) throws MalformedURLException {
            super(new URL("http://127.0.0.1"));this.status=status;this.body=body;
        }
        @Override public void connect() {}
        @Override public void disconnect() {disconnected=true;}
        @Override public boolean usingProxy() {return false;}
        @Override public int getResponseCode() {return status;}
        @Override public InputStream getInputStream() {return new ByteArrayInputStream(body);}
    }
}
