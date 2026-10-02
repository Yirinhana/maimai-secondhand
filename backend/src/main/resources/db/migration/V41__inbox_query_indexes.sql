CREATE INDEX idx_notifications_inbox_time ON notifications(user_id,type,created_at);
CREATE INDEX idx_notifications_target ON notifications(user_id,type,target_path);
