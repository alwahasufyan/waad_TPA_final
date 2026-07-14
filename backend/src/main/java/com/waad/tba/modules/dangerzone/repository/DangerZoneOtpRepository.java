package com.waad.tba.modules.dangerzone.repository;

import com.waad.tba.modules.dangerzone.entity.DangerZoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DangerZoneOtpRepository extends JpaRepository<DangerZoneOtp, Long> {

    Optional<DangerZoneOtp> findTopByUsernameAndOperationOrderByCreatedAtDesc(String username, String operation);

    Optional<DangerZoneOtp> findTopByUsernameAndOperationAndConsumedFalseOrderByCreatedAtDesc(String username, String operation);
}
