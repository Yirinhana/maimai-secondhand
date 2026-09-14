package com.maimai.trade.repo;

import com.maimai.trade.domain.MeetupAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetupAppointmentRepository extends JpaRepository<MeetupAppointment, Long> {

    Optional<MeetupAppointment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
