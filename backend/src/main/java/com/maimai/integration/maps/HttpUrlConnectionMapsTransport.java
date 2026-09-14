package com.maimai.integration.maps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.net.SocketTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 基于 JDK HttpURLConnection 的实现。
 * <ul>
 *   <li>连接超时 3 秒，读取超时 8 秒</li>
 *   <li>不跟随重定向（手动关闭重定向），避免跨域与读阻塞</li>
 *   <li>响应体最多读取 1 MB，超出立即拒绝；读完断开连接</li>
 *   <li>固定访问 https://restapi.amap.com ，不接受调用方传入 baseUrl</li>
 *   <li>异常消息不包含查询串/密钥</li>
 * </ul>
 */
@Component
public class HttpUrlConnectionMapsTransport implements MapsTransport {

    static final String AMAP_HOST = "https://restapi.amap.com";
    static final int CONNECT_TIMEOUT_MS = 3_000;
    static final int READ_TIMEOUT_MS = 8_000;
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final String apiKey;
    private final ConnectionFactory connections;
    @FunctionalInterface interface ConnectionFactory {HttpURLConnection open(URL url) throws IOException;}

    @Autowired
    public HttpUrlConnectionMapsTransport(MapsProperties properties) {this(properties.getKey());}

    public HttpUrlConnectionMapsTransport(String apiKey) {
        this(apiKey, url -> (HttpURLConnection)url.openConnection());
    }
    HttpUrlConnectionMapsTransport(String apiKey, ConnectionFactory connections) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.connections=connections;
    }

    @Override
    public String get(String path, Map<String, String> params) throws IOException {
        try {return request(path,params);} catch(IOException ex) {throw new IOException("Map transport unavailable");}
    }

    private String request(String path, Map<String, String> params) throws IOException {
        if (path == null || !Set.of("/v3/geocode/geo","/v3/geocode/regeo","/v3/place/around","/v5/direction/driving").contains(path)) {
            throw new IOException("invalid path");
        }
        if (apiKey.isEmpty()) {
            throw new IOException("amap key not configured");
        }
        StringBuilder url = new StringBuilder(AMAP_HOST);
        if (!path.startsWith("/")) {
            url.append('/');
        }
        url.append(path);
        url.append('?');
        url.append("key=").append(encode(apiKey));
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                if("key".equals(e.getKey())) throw new IOException("invalid parameter");
                url.append('&').append(encode(e.getKey())).append('=').append(encode(e.getValue()));
            }
        }

        HttpURLConnection conn = connections.open(new URL(url.toString()));
        long deadline=System.nanoTime()+11_000_000_000L;
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "maimai-maps/1.0");
            conn.connect();

            int status = conn.getResponseCode();
            if(status<200 || status>=300) throw new IOException("Map provider HTTP error");
            InputStream stream = conn.getInputStream();
            if (stream == null) {
                throw new IOException("amap http " + status);
            }
            try (InputStream in = stream) {
                return readCapped(in, MAX_BODY_BYTES,conn,deadline);
            }
        } finally {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String readCapped(InputStream in, int max,HttpURLConnection connection,long deadline) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 4096));
        byte[] buf = new byte[2048];
        int total = 0;
        int n;
        while (true) {
            long remaining=(deadline-System.nanoTime())/1_000_000;
            if(remaining<=0) throw new SocketTimeoutException("Map response deadline exceeded");
            connection.setReadTimeout((int)Math.min(READ_TIMEOUT_MS,Math.max(1,remaining)));
            n=in.read(buf);
            if(n==-1) break;
            total += n;
            if (total > max) {
                throw new IOException("Map response exceeds size limit");
            }
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
