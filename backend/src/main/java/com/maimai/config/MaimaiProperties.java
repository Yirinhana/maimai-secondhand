package com.maimai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用级配置：凭据一律来自环境变量，配置文件只放样例与默认值。 */
@ConfigurationProperties(prefix = "maimai")
public class MaimaiProperties {

    private String uploadDir = ".local/uploads";
    private String frontendOrigin = "http://localhost:5173";
    private final Mail mail = new Mail();
    private final Payment payment = new Payment();

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
    public String getFrontendOrigin() { return frontendOrigin; }
    public void setFrontendOrigin(String frontendOrigin) { this.frontendOrigin = frontendOrigin; }
    public Mail getMail() { return mail; }
    public Payment getPayment() { return payment; }

    public static class Mail {
        /** capture=本地捕获；ses=腾讯云SES（待真实接入） */
        private String provider = "capture";
        private String from = "noreply@maimai.local";
        private String captureDir = ".local/mail-capture";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getCaptureDir() { return captureDir; }
        public void setCaptureDir(String captureDir) { this.captureDir = captureDir; }
    }

    public static class Payment {
        /** mock=隔离模拟渠道（仅 local）；wechat=微信支付（须真实凭据） */
        private String channel = "mock";
        private final Wechat wechat = new Wechat();

        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public Wechat getWechat() { return wechat; }

        public static class Wechat {
            private String mchId = "";
            private String appId = "";
            private String apiKey = "";
            private String certPath = "";

            public String getMchId() { return mchId; }
            public void setMchId(String mchId) { this.mchId = mchId; }
            public String getAppId() { return appId; }
            public void setAppId(String appId) { this.appId = appId; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getCertPath() { return certPath; }
            public void setCertPath(String certPath) { this.certPath = certPath; }

            public boolean configured() {
                return !mchId.isBlank() && !appId.isBlank() && !apiKey.isBlank();
            }
        }
    }
}
