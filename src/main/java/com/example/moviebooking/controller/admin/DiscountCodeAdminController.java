package com.example.moviebooking.controller.admin;

import com.example.moviebooking.dto.DiscountCodeRequest;
import com.example.moviebooking.entity.DiscountCode;
import com.example.moviebooking.exception.ResourceNotFoundException;
import com.example.moviebooking.repository.DiscountCodeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/discount-codes")
@RequiredArgsConstructor
public class DiscountCodeAdminController {

    private final DiscountCodeRepository discountCodeRepository;

    @PostMapping
    public ResponseEntity<DiscountCode> create(@Valid @RequestBody DiscountCodeRequest request) {
        validateExactlyOneDiscountType(request);

        DiscountCode dc = DiscountCode.builder()
                .code(request.code())
                .percentageOff(request.percentageOff())
                .flatAmountOff(request.flatAmountOff())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .maxRedemptions(request.maxRedemptions())
                .timesRedeemed(0)
                .active(request.active() == null || request.active())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(discountCodeRepository.save(dc));
    }

    @GetMapping
    public List<DiscountCode> list() {
        return discountCodeRepository.findAll();
    }

    @GetMapping("/{id}")
    public DiscountCode get(@PathVariable Long id) {
        return findOrThrow(id);
    }

    @PutMapping("/{id}")
    public DiscountCode update(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        validateExactlyOneDiscountType(request);

        DiscountCode dc = findOrThrow(id);
        dc.setCode(request.code());
        dc.setPercentageOff(request.percentageOff());
        dc.setFlatAmountOff(request.flatAmountOff());
        dc.setValidFrom(request.validFrom());
        dc.setValidUntil(request.validUntil());
        dc.setMaxRedemptions(request.maxRedemptions());
        if (request.active() != null) {
            dc.setActive(request.active());
        }
        return discountCodeRepository.save(dc);
    }

    // Deactivating rather than deleting preserves history for bookings that
    // already redeemed this code - deleting the row would orphan
    // Booking.discountCode references.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        DiscountCode dc = findOrThrow(id);
        dc.setActive(false);
        discountCodeRepository.save(dc);
        return ResponseEntity.noContent().build();
    }

    private void validateExactlyOneDiscountType(DiscountCodeRequest request) {
        boolean hasPercentage = request.percentageOff() != null;
        boolean hasFlat = request.flatAmountOff() != null;
        if (hasPercentage == hasFlat) { // both null or both set
            throw new IllegalArgumentException("Exactly one of percentageOff or flatAmountOff must be set");
        }
    }

    private DiscountCode findOrThrow(Long id) {
        return discountCodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount code not found: " + id));
    }
}
