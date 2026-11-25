package za.co.salon.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import za.co.salon.business.entity.Business;
import za.co.salon.business.entity.BusinessMember;
import za.co.salon.business.repository.BusinessMemberRepository;
import za.co.salon.business.repository.BusinessRepository;
import za.co.salon.identity.entity.User;
import za.co.salon.identity.repository.UserRepository;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for the central authorization bean. The web-layer suites
 * cover the integration behavior; these cover branches that need direct control
 * of the security and request context (no membership at all, role mismatches,
 * the self-or-manager rule). Each scenario installs a fresh mock request so the
 * request-scoped BusinessContext never leaks between cases.
 */
@SpringBootTest
@Transactional
class BusinessAuthorizationServiceTest {

    @Autowired
    private BusinessAuthorizationService authz;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMemberRepository memberRepository;

    @AfterEach
    void clearHolders() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void hasRoleIsTrueForMatchingRoleAndFalseOtherwise() {
        User owner = createUser("role.owner");
        Business business = createBusiness("Role Biz");
        createMembership(owner, business, BusinessMember.Role.OWNER);
        installContext(owner.getId(), null);

        assertThat(authz.hasRole("OWNER")).isTrue();
        assertThat(authz.hasRole("OWNER", "MANAGER")).isTrue();
        assertThat(authz.hasRole("MANAGER")).isFalse();
        assertThat(authz.hasRole("RECEPTIONIST")).isFalse();
    }

    @Test
    void requireMembershipThrowsWhenUserHasNoMembershipAtAll() {
        User lone = createUser("lone");
        installContext(lone.getId(), null);

        assertThatThrownBy(() -> authz.requireMembership())
                .isInstanceOf(AccessDeniedException.class);
        assertThat(authz.hasRole("OWNER")).isFalse();
    }

    @Test
    void headerNamingForeignBusinessIsDenied() {
        User user = createUser("foreign");
        Business own = createBusiness("Own Biz");
        Business foreign = createBusiness("Foreign Biz");
        createMembership(user, own, BusinessMember.Role.OWNER);
        installContext(user.getId(), foreign.getId());

        assertThatThrownBy(() -> authz.requireMembership())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireRoleThrowsForWrongRole() {
        User stylist = createUser("stylist");
        Business business = createBusiness("Stylist Biz");
        createMembership(stylist, business, BusinessMember.Role.STYLIST);
        installContext(stylist.getId(), null);

        assertThatThrownBy(() -> authz.requireRole("OWNER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isSelfOrManagerIsTrueForOwnerRegardlessOfEmployee() {
        User owner = createUser("self.owner");
        Business business = createBusiness("Self Biz");
        createMembership(owner, business, BusinessMember.Role.OWNER);
        installContext(owner.getId(), null);

        assertThat(authz.isSelfOrManager(999L)).isTrue();
    }

    @Test
    void isSelfOrManagerIsFalseForStaffWithoutEmployeeLink() {
        // employeeId on BusinessContext stays null until phase 3 links employees,
        // so a stylist can never satisfy the self-check yet - document that contract.
        User stylist = createUser("self.stylist");
        Business business = createBusiness("Self Biz 2");
        createMembership(stylist, business, BusinessMember.Role.STYLIST);
        installContext(stylist.getId(), null);

        assertThat(authz.isSelfOrManager(999L)).isFalse();
    }

    /**
     * Installs a fresh security context (principal = user id, as the JWT filter sets)
     * and a fresh mock request carrying the optional X-Business-Id header.
     */
    private void installContext(Long userId, Long businessIdHeader) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (businessIdHeader != null) {
            request.addHeader("X-Business-Id", String.valueOf(businessIdHeader));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private User createUser(String emailPrefix) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(emailPrefix + "." + System.nanoTime() + "@test.com");
        user.setPasswordHash("$2a$10$notarealhashbutformatisvalid");
        return userRepository.saveAndFlush(user);
    }

    private Business createBusiness(String name) {
        Business business = new Business();
        business.setName(name);
        business.setAddressLine1("1 Main");
        business.setCity("JHB");
        business.setProvince("GP");
        return businessRepository.saveAndFlush(business);
    }

    private void createMembership(User user, Business business, BusinessMember.Role role) {
        BusinessMember membership = new BusinessMember();
        membership.setUserId(user.getId());
        membership.setBusinessId(business.getId());
        membership.setRole(role);
        membership.setStatus(BusinessMember.Status.ACTIVE);
        memberRepository.saveAndFlush(membership);
    }
}
