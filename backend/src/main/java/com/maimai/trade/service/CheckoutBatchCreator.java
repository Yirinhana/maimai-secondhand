package com.maimai.trade.service;

import com.maimai.common.NoGenerator;
import com.maimai.trade.domain.CheckoutBatch;
import com.maimai.trade.repo.CheckoutBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结算批次独立事务创建：唯一约束冲突时只回滚本事务，
 * 由调用方捕获后重查返回已有批次，避免污染结算主事务。
 */
@Service
public class CheckoutBatchCreator {

    private final CheckoutBatchRepository checkoutBatchRepository;

    public CheckoutBatchCreator(CheckoutBatchRepository checkoutBatchRepository) {
        this.checkoutBatchRepository = checkoutBatchRepository;
    }

    @Transactional
    public CheckoutBatch create(Long userId, String idempotencyKey) {
        CheckoutBatch batch = new CheckoutBatch();
        batch.setBatchNo(NoGenerator.next("MB"));
        batch.setUserId(userId);
        batch.setIdempotencyKey(idempotencyKey);
        return checkoutBatchRepository.saveAndFlush(batch);
    }
}
