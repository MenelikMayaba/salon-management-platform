package za.co.salon.business.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.business.dto.AddMemberRequest;
import za.co.salon.business.dto.BusinessRequest;
import za.co.salon.business.dto.BusinessResponse;
import za.co.salon.business.dto.MemberResponse;
import za.co.salon.business.entity.Business;
import za.co.salon.business.onboarding.BusinessOnboardingHandler;
import za.co.salon.business.entity.BusinessMember;
import za.co.salon.business.repository.BusinessMemberRepository;
import za.co.salon.business.repository.BusinessRepository;
import za.co.salon.identity.entity.User;
import za.co.salon.identity.repository.UserRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;
import za.co.salon.shared.security.SecurityUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;
    private final List<BusinessOnboardingHandler> onboardingHandlers;

    public BusinessService(BusinessRepository businessRepository,
                           BusinessMemberRepository memberRepository,
                           UserRepository userRepository,
                           BusinessAuthorizationService authz,
                           BusinessContext businessContext,
                           List<BusinessOnboardingHandler> onboardingHandlers) {
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.authz = authz;
        this.businessContext = businessContext;
        this.onboardingHandlers = onboardingHandlers;
    }

    /**
     * Creates the business and the caller's OWNER membership atomically -
     * a business without an owner, or a membership without its business,
     * must never exist.
     */
    @Transactional
    public BusinessResponse createBusiness(BusinessRequest request) {
        Business business = new Business();
        applyRequest(business, request);
        businessRepository.save(business);

        BusinessMember membership = new BusinessMember();
        membership.setBusinessId(business.getId());
        membership.setUserId(SecurityUtils.currentUserId());
        membership.setRole(BusinessMember.Role.OWNER);
        membership.setStatus(BusinessMember.Status.ACTIVE);
        memberRepository.save(membership);

        // Onboarding (chart of accounts, sequence counters) runs in this same
        // transaction: if seeding fails, the business creation rolls back too.
        for (BusinessOnboardingHandler handler : onboardingHandlers) {
            handler.onBusinessCreated(business);
        }

        return toResponse(business);
    }

    @Transactional(readOnly = true)
    public BusinessResponse getCurrentBusiness() {
        authz.requireMembership();
        Business business = loadCurrent();
        return toResponse(business);
    }

    @Transactional
    public BusinessResponse updateCurrentBusiness(BusinessRequest request) {
        authz.requireMembership();
        Business business = loadCurrent();
        applyRequest(business, request);
        businessRepository.save(business);
        return toResponse(business);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        authz.requireMembership();
        List<BusinessMember> members = memberRepository.findByBusinessId(businessContext.getBusinessId());

        // Batch-fetch all member users in a single query (blueprint rule 15:
        // never a repository call inside a loop - that was the phase-2 N+1 here).
        List<Long> userIds = new ArrayList<>();
        for (BusinessMember member : members) {
            userIds.add(member.getUserId());
        }
        Map<Long, User> usersById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                usersById.put(user.getId(), user);
            }
        }

        List<MemberResponse> responses = new ArrayList<>();
        for (BusinessMember member : members) {
            MemberResponse response = new MemberResponse();
            response.setId(member.getId());
            response.setUserId(member.getUserId());
            response.setRole(member.getRole().name());
            response.setStatus(member.getStatus().name());
            response.setJoinedAt(member.getJoinedAt());
            User user = usersById.get(member.getUserId());
            if (user != null) {
                response.setEmail(user.getEmail());
                response.setFirstName(user.getFirstName());
                response.setLastName(user.getLastName());
            }
            responses.add(response);
        }
        return responses;
    }

    @Transactional
    public MemberResponse addMember(AddMemberRequest request) {
        authz.requireMembership();

        Optional<User> found = userRepository.findByEmail(request.getEmail());
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email");
        }
        User user = found.get();

        Long businessId = businessContext.getBusinessId();
        if (memberRepository.existsByUserIdAndBusinessId(user.getId(), businessId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this business");
        }

        BusinessMember member = new BusinessMember();
        member.setBusinessId(businessId);
        member.setUserId(user.getId());
        member.setRole(BusinessMember.Role.valueOf(request.getRole()));
        member.setStatus(BusinessMember.Status.ACTIVE);
        memberRepository.save(member);

        MemberResponse response = new MemberResponse();
        response.setId(member.getId());
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setRole(member.getRole().name());
        response.setStatus(member.getStatus().name());
        response.setJoinedAt(member.getJoinedAt());
        return response;
    }

    private Business loadCurrent() {
        Optional<Business> found = businessRepository.findById(businessContext.getBusinessId());
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found");
        }
        return found.get();
    }

    private void applyRequest(Business business, BusinessRequest request) {
        business.setName(request.getName());
        business.setRegistrationNumber(request.getRegistrationNumber());
        business.setEmail(request.getEmail());
        business.setPhoneNumber(request.getPhoneNumber());
        business.setAddressLine1(request.getAddressLine1());
        business.setAddressLine2(request.getAddressLine2());
        business.setCity(request.getCity());
        business.setProvince(request.getProvince());
        business.setPostalCode(request.getPostalCode());
    }

    private BusinessResponse toResponse(Business business) {
        BusinessResponse response = new BusinessResponse();
        response.setId(business.getId());
        response.setName(business.getName());
        response.setEmail(business.getEmail());
        response.setPhoneNumber(business.getPhoneNumber());
        response.setAddressLine1(business.getAddressLine1());
        response.setAddressLine2(business.getAddressLine2());
        response.setCity(business.getCity());
        response.setProvince(business.getProvince());
        response.setPostalCode(business.getPostalCode());
        response.setCountryCode(business.getCountryCode());
        response.setCurrencyCode(business.getCurrencyCode());
        response.setTimezone(business.getTimezone());
        return response;
    }
}
