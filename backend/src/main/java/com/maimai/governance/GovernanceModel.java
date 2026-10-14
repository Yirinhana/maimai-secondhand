package com.maimai.governance;

public interface GovernanceModel {
    boolean configured();
    String model();
    Assessment assess(GovernanceSnapshot.Snapshot snapshot);
    record Assessment(String category,String priority,String summary,String evidence,String missing,String suggestion) {}
}
