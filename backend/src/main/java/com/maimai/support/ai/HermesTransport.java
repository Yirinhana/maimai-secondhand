package com.maimai.support.ai;
import java.net.URI;
public interface HermesTransport {
    String post(URI endpoint,String token,String body);
}
