package za.co.salon.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import za.co.salon.business.entity.BusinessMember;
import za.co.salon.business.repository.BusinessMemberRepository;

import java.util.Arrays;
import java.util.List;

/**
 * The single owner of every authorization rule in the system (blueprint 7).
 * Endpoint gates delegate here via @PreAuthorize("@authz.hasRole(...)") and
 * services call it directly for row-ownership checks. Centralizing the rules
 * means one place to audit and one place to test - enforcement points stay thin.
 */
@Service("authz")
public class BusinessAuthorizationService {

    private final BusinessMemberRepository memberRepository;
    private final BusinessContext businessContext;

    public BusinessAuthorizationService(BusinessMemberRepository memberRepository,
                                        BusinessContext businessContext) {
        this.memberRepository = memberRepository;
        this.businessContext = businessContext;
    }

    /**
     * True when the caller's active membership has one of the given roles.
     * Used by @PreAuthorize; must return false rather than throw so that
     * denied requests become 403 instead of 500.
     */
    public boolean hasRole(String... roles) {
        try {
            resolveContext();
        } catch (AccessDeniedException exception) {
            return false;
        }
        if (businessContext.getRole() == null) {
            return false;
        }
        return Arrays.asList(roles).contains(businessContext.getRole());
    }

    /**
     * Throws unless the caller has an ACTIVE membership in the resolved business.
     * Returns true on success so it can be used directly in @PreAuthorize -
     * for endpoints open to any staff role.
     */
    public boolean requireMembership() {
        resolveContext();
        return true;
    }

    /**
     * Throws unless the caller's active membership has one of the given roles.
     * For use inside services where an annotation is impractical.
     */
    public void requireRole(String... roles) {
        if (!hasRole(roles)) {
            throw new AccessDeniedException("Required role: " + String.join(",", roles));
        }
    }

    /**
     * The row-level check: throws unless the entity belongs to the caller's
     * active business. Must be called by services whenever an entity is loaded
     * by an externally supplied id (blueprint 2.3 step 4).
     */
    public void assertBusinessOwnership(BusinessScoped entity) {
        resolveContext();
        if (entity == null || entity.getBusinessId() == null
                || !entity.getBusinessId().equals(businessContext.getBusinessId())) {
            throw new AccessDeniedException("Record does not belong to your business");
        }
    }

    /**
     * True when the caller is the given employee themselves, or holds
     * OWNER/MANAGER. For "own data" endpoints such as a stylist reading
     * their own commissions.
     */
    public boolean isSelfOrManager(Long employeeId) {
        try {
            resolveContext();
        } catch (AccessDeniedException exception) {
            return false;
        }
        if (hasRole("OWNER", "MANAGER")) {
            return true;
        }
        return employeeId != null && employeeId.equals(businessContext.getEmployeeId());
    }

    /**
     * Resolves the caller's active business membership once per request into
     * the BusinessContext. The active business is selected by the X-Business-Id
     * header, defaulting to the user's single ACTIVE membership. The membership
     * row must exist and be ACTIVE - a header naming a business the user does
     * not belong to is denied, never trusted (blueprint 2.3).
     */
    private void resolveContext() {
        if (businessContext.isResolved()) {
            return;
        }

        Long userId = SecurityUtils.currentUserId();
        List<BusinessMember> activeMemberships =
                memberRepository.findByUserIdAndStatus(userId, BusinessMember.Status.ACTIVE);

        if (activeMemberships.isEmpty()) {
            throw new AccessDeniedException("No active business membership");
        }

        String requestedBusinessId = requestedBusinessIdHeader();
        BusinessMember selected = null;

        if (requestedBusinessId != null && !requestedBusinessId.isBlank()) {
            Long businessId = parseBusinessId(requestedBusinessId);
            for (BusinessMember membership : activeMemberships) {
                if (membership.getBusinessId().equals(businessId)) {
                    selected = membership;
                    break;
                }
            }
            if (selected == null) {
                throw new AccessDeniedException("Not a member of the requested business");
            }
        } else if (activeMemberships.size() == 1) {
            selected = activeMemberships.get(0);
        } else {
            throw new AccessDeniedException("Multiple businesses - specify the X-Business-Id header");
        }

        businessContext.setUserId(userId);
        businessContext.setBusinessId(selected.getBusinessId());
        businessContext.setRole(selected.getRole().name());
        businessContext.setMembershipStatus(selected.getStatus().name());
        businessContext.setResolved(true);
    }

    private String requestedBusinessIdHeader() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader("X-Business-Id");
    }

    private Long parseBusinessId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Invalid X-Business-Id header");
        }
    }
}
