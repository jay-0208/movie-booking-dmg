package com.example.moviebooking.repository;

import com.example.moviebooking.entity.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
    List<RefundPolicy> findByActiveTrueOrderByMinHoursBeforeShowDesc();
}
