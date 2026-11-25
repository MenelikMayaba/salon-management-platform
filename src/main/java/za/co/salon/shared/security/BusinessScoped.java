package za.co.salon.shared.security;

/**
 * Implemented by every business-owned entity so that
 * BusinessAuthorizationService.assertBusinessOwnership has a single signature
 * (blueprint 7.1).
 */
public interface BusinessScoped {

    Long getBusinessId();
}
