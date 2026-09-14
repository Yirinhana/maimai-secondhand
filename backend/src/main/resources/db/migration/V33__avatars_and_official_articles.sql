CREATE TABLE user_avatars (
  user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  filename VARCHAR(40) NOT NULL,
  byte_size INT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_avatar_filename (filename),
  CONSTRAINT fk_avatar_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE official_articles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title VARCHAR(120) NOT NULL,
  summary VARCHAR(300) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  category VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  published_at DATETIME(6) NULL,
  created_by BIGINT UNSIGNED NULL,
  updated_by BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_official_slug (slug),
  KEY idx_official_publication (status,category,published_at),
  CONSTRAINT chk_official_category CHECK (category IN ('NOTICE','GUIDE','SAFETY','ABOUT')),
  CONSTRAINT chk_official_status CHECK (status IN ('DRAFT','PUBLISHED','WITHDRAWN')),
  CONSTRAINT chk_official_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
  CONSTRAINT fk_official_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_official_editor FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO official_articles(slug,title,summary,body,category,status,published_at) VALUES
('about-maimai','让闲置物品遇见下一位主人','了解麦麦二手的服务范围、交易方式与当前本地版本状态。',
'麦麦二手是面向普通用户的二手实物信息管理与交易项目。你可以浏览闲置物品、发布商品与求购信息，通过站内私信沟通，并查看订单与售后进度。

商品支持快递和同城面交。卖家应如实展示品相、缺陷、价格、库存与交付条件，买家应在下单前确认这些信息。

当前展示的是课程项目的本地开发版本。网站尚未完成公开运营验收，真实微信支付、退款与分账仍需取得相应资格并完成渠道开通和验证。本地模拟付款仅用于演示，不代表真实资金转移。',
'ABOUT','PUBLISHED',CURRENT_TIMESTAMP(6)),
('buying-and-selling-guide','第一次买卖，从这里开始','从发布、议价、付款到交付，了解双方可以如何完成一笔订单。',
'买家可以先浏览分类和商品详情，核对品相、缺陷、价格、可配送地区及运费。对价格有疑问时，可通过站内私信沟通并使用议价功能；议价结果有有效期，请以下单时展示的金额为准。

购物车商品按卖家拆分订单并分别付款。商品款与运费会分别展示；平台服务费按实际成交商品金额的0.03%计算，不含运费，四舍五入到分，可能为0。渠道手续费独立展示，真实渠道尚未确认的金额不会作为0元承诺。

卖家发布商品后需等待审核，关键修改后会再次审核。快递订单由卖家填写承运商和运单号；面交由双方约定时间与地点。请在实际拿到物品并完成核对后，再出示一次性交付码或确认收货。

出现问题时，请从订单申请售后，填写原因并保存证据。具体处理时限及退货责任以网站用户协议与交易售后规则为准。',
'GUIDE','PUBLISHED',CURRENT_TIMESTAMP(6)),
('trade-safety','把确认留在交易现场','识别可疑请求，保护验证码、地址与交易证据。',
'请通过站内记录核对商品描述、议价结果和交付约定。遇到催促绕开订单付款、索要邮箱验证码、密码或一次性交付码的请求，应停止操作并通过举报或客服入口反馈。

收货地址和联系电话只在交付必要范围内使用。不要在公开商品、评论或求购信息中发布完整地址、证件照片、付款凭据中的敏感信息。

同城面交可约在方便核验物品的公共地点。先核对物品与约定是否一致，再由买家主动出示交付码；不要提前发送交付码，也不要仅凭截图判断付款成功。

如发生争议，请保存商品描述、聊天、物流与实物照片，并通过订单售后入口提交。管理员会依据已提交证据处理平台流程，不能保证所有争议都能自动解决。',
'SAFETY','PUBLISHED',CURRENT_TIMESTAMP(6)),
('local-development-notice','本地体验版本说明','当前可体验哪些流程，以及哪些外部服务仍待配置和真实验收。',
'本版本用于本地开发、课程演示和验收。已提供商品浏览、账户资料、发布审核、订单、模拟付款、售后和管理功能，具体可用流程以当前页面和配置为准。

本地模拟支付不扣取真实资金，也不产生真实分账。真实微信支付、退款和分账需在渠道资质具备、服务开通并完成验证后启用。邮件、物流查询和智能客服依赖相应服务配置，未配置时会明确提示不可用，不会伪造发送、轨迹或答复成功。

网站用户协议与交易规则目前标为本地草案。公开部署、正式运营主体、客服联系方式及其他上线条件仍需后续确认；本页不代表已经取得这些资质或完成上线。

欢迎在体验时记录问题发生的页面、操作步骤和提示内容，再通过站内反馈入口提交。请勿上传与测试无关的敏感资料。',
'NOTICE','PUBLISHED',CURRENT_TIMESTAMP(6));
