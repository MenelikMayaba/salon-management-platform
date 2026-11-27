package za.co.salon.business.onboarding;

import za.co.salon.business.entity.Business;

/**
 * Seam for module-specific setup that must happen when a business is created.
 * Implementations run inside the business-creation transaction, so a seeding
 * failure rolls the whole creation back - a business without its chart of
 * accounts must never exist.
 */
public interface BusinessOnboardingHandler {

    void onBusinessCreated(Business business);
}
