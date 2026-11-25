package za.co.salon.business.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.business.dto.AddMemberRequest;
import za.co.salon.business.dto.BusinessRequest;
import za.co.salon.business.dto.BusinessResponse;
import za.co.salon.business.dto.MemberResponse;
import za.co.salon.business.service.BusinessService;

import java.util.List;

/**
 * Endpoints per blueprint section 9. Gates are thin annotations delegating to
 * the central authz bean - the rules live in BusinessAuthorizationService.
 */
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    /**
     * Any authenticated user can create a business; they become its OWNER.
     */
    @PostMapping
    public ResponseEntity<BusinessResponse> createBusiness(@Valid @RequestBody BusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.createBusiness(request));
    }

    @GetMapping("/current")
    @PreAuthorize("@authz.requireMembership()")
    public BusinessResponse getCurrentBusiness() {
        return businessService.getCurrentBusiness();
    }

    @PutMapping("/current")
    @PreAuthorize("@authz.hasRole('OWNER')")
    public BusinessResponse updateCurrentBusiness(@Valid @RequestBody BusinessRequest request) {
        return businessService.updateCurrentBusiness(request);
    }

    @GetMapping("/current/members")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public List<MemberResponse> listMembers() {
        return businessService.listMembers();
    }

    @PostMapping("/current/members")
    @PreAuthorize("@authz.hasRole('OWNER')")
    public ResponseEntity<MemberResponse> addMember(@Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.addMember(request));
    }
}
