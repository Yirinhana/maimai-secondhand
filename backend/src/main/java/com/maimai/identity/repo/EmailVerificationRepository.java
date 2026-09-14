package com.maimai.identity.repo;

import com.maimai.identity.domain.EmailVerification;
import com.maimai.identity.domain.EmailVerification.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, Purpose purpose);
}
