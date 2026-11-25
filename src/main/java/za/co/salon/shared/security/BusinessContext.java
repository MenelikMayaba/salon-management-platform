package za.co.salon.shared.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * The caller's active business identity for the duration of one request
 * (blueprint 7.1). Populated lazily by BusinessAuthorizationService from the
 * X-Business-Id header or the user's sole ACTIVE membership. Never trust a
 * businessId that was not resolved through the membership check.
 */
@Component
@RequestScope
public class BusinessContext {

    private Long userId;
    private Long businessId;
    private String role;
    private String membershipStatus;
    private Long employeeId;
    private boolean resolved;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(String membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
