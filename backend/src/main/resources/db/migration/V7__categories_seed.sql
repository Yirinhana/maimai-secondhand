-- 基础分类参考数据（业务无关环境差异，始终需要）
INSERT INTO categories (name, parent_id, sort, status) VALUES
  ('数码电子', NULL, 1, 'ACTIVE'),
  ('手机', (SELECT id FROM (SELECT id FROM categories WHERE name='数码电子') t), 1, 'ACTIVE'),
  ('电脑及配件', (SELECT id FROM (SELECT id FROM categories WHERE name='数码电子') t), 2, 'ACTIVE'),
  ('影音家电', (SELECT id FROM (SELECT id FROM categories WHERE name='数码电子') t), 3, 'ACTIVE'),
  ('服饰鞋包', NULL, 2, 'ACTIVE'),
  ('图书教材', NULL, 3, 'ACTIVE'),
  ('生活家居', NULL, 4, 'ACTIVE'),
  ('运动户外', NULL, 5, 'ACTIVE'),
  ('美妆个护', NULL, 6, 'ACTIVE'),
  ('其他闲置', NULL, 9, 'ACTIVE');
