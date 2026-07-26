package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.RefundPolicyRequest;
import com.example.moviebooking.entity.RefundPolicy;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.RefundPolicyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/refund-policies")
@RequiredArgsConstructor
public class RefundPolicyAdminController {

    private final RefundPolicyRepository refundPolicyRepository;

    @PostMapping
    public ResponseEntity<RefundPolicy> create(@Valid @RequestBody RefundPolicyRequest request) {
        RefundPolicy policy = RefundPolicy.builder()
                .name(request.name())
                .minHoursBeforeShow(request.minHoursBeforeShow())
                .refundPercentage(request.refundPercentage())
                .active(request.active() == null || request.active())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(refundPolicyRepository.save(policy));
    }

    @GetMapping
    public List<RefundPolicy> list() {
        return refundPolicyRepository.findAll();
    }

    @GetMapping("/{id}")
    public RefundPolicy get(@PathVariable Long id) {
        return findOrThrow(id);
    }

    @PutMapping("/{id}")
    public RefundPolicy update(@PathVariable Long id, @Valid @RequestBody RefundPolicyRequest request) {
        RefundPolicy policy = findOrThrow(id);
        policy.setName(request.name());
        policy.setMinHoursBeforeShow(request.minHoursBeforeShow());
        policy.setRefundPercentage(request.refundPercentage());
        if (request.active() != null) {
            policy.setActive(request.active());
        }
        return refundPolicyRepository.save(policy);
    }

    // Same reasoning as DiscountCodeAdminController: deactivate, don't delete -
    // CancellationService reads active policies at cancellation time, and past
    // cancellations may have relied on a policy that's since been retired.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        RefundPolicy policy = findOrThrow(id);
        policy.setActive(false);
        refundPolicyRepository.save(policy);
        return ResponseEntity.noContent().build();
    }

    private RefundPolicy findOrThrow(Long id) {
        return refundPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund policy not found: " + id));
    }
}
