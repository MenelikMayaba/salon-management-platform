package za.co.salon.catalogue.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.business.entity.BusinessMember;
import za.co.salon.business.repository.BusinessMemberRepository;
import za.co.salon.catalogue.dto.CommissionRuleRequest;
import za.co.salon.catalogue.dto.CommissionRuleResponse;
import za.co.salon.catalogue.dto.EmployeeRequest;
import za.co.salon.catalogue.dto.EmployeeResponse;
import za.co.salon.catalogue.entity.CommissionRule;
import za.co.salon.catalogue.entity.Employee;
import za.co.salon.catalogue.repository.CommissionRuleRepository;
import za.co.salon.catalogue.repository.EmployeeRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

import java.util.List;
import java.util.Optional;

/**
 * Employees and their commission rules. Two cross-entity rules from the blueprint:
 * a linked user must be an ACTIVE member of the same business, and overlapping
 * ACTIVE commission ranges for one employee are rejected.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CommissionRuleRepository commissionRuleRepository;
    private final BusinessMemberRepository memberRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public EmployeeService(EmployeeRepository employeeRepository,
                           CommissionRuleRepository commissionRuleRepository,
                           BusinessMemberRepository memberRepository,
                           BusinessAuthorizationService authz,
                           BusinessContext businessContext) {
        this.employeeRepository = employeeRepository;
        this.commissionRuleRepository = commissionRuleRepository;
        this.memberRepository = memberRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Employee employee = new Employee();
        employee.setBusinessId(businessContext.getBusinessId());
        applyRequest(employee, request);
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeeRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = loadOwnedEmployee(id);
        applyRequest(employee, request);
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public CommissionRuleResponse createCommissionRule(Long employeeId, CommissionRuleRequest request) {
        Employee employee = loadOwnedEmployee(employeeId);
        rejectOverlappingActiveRule(employee.getId(), request);
        CommissionRule rule = new CommissionRule();
        rule.setBusinessId(businessContext.getBusinessId());
        rule.setEmployeeId(employee.getId());
        rule.setPercentageRate(request.getPercentageRate());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setActive(request.isActive());
        return toResponse(commissionRuleRepository.save(rule));
    }

    private void applyRequest(Employee employee, EmployeeRequest request) {
        validateLinkedUserIsMember(request.getUserId());
        employee.setUserId(request.getUserId());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setPhoneNumber(request.getPhoneNumber());
        employee.setEmploymentStatus(parseEmploymentStatus(request.getEmploymentStatus()));
        employee.setEmploymentStartDate(request.getEmploymentStartDate());
    }

    /**
     * A linked login account must belong to an ACTIVE member of this business.
     * Employees without accounts are fine - null userId skips validation.
     */
    private void validateLinkedUserIsMember(Long userId) {
        if (userId == null) {
            return;
        }
        Optional<BusinessMember> membership =
                memberRepository.findByUserIdAndBusinessId(userId, businessContext.getBusinessId());
        if (membership.isEmpty() || membership.get().getStatus() != BusinessMember.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Linked user is not an active member of this business");
        }
    }

    /**
     * Overlap rule: two ranges overlap when each starts before the other ends.
     * A null effectiveTo is open-ended, treated as "never ends". Only ACTIVE
     * rules participate - a deactivated rule imposes no constraint.
     */
    private void rejectOverlappingActiveRule(Long employeeId, CommissionRuleRequest request) {
        if (!request.isActive()) {
            return;
        }
        List<CommissionRule> existingRules = commissionRuleRepository.findAllByEmployeeIdAndActiveTrue(employeeId);
        for (CommissionRule existing : existingRules) {
            if (rangesOverlap(existing.getEffectiveFrom(), existing.getEffectiveTo(),
                    request.getEffectiveFrom(), request.getEffectiveTo())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Commission rule overlaps an existing active rule for this employee");
            }
        }
    }

    private boolean rangesOverlap(java.time.LocalDate firstFrom, java.time.LocalDate firstTo,
                                  java.time.LocalDate secondFrom, java.time.LocalDate secondTo) {
        boolean firstStartsBeforeSecondEnds = secondTo == null || !firstFrom.isAfter(secondTo);
        boolean secondStartsBeforeFirstEnds = firstTo == null || !secondFrom.isAfter(firstTo);
        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }

    private Employee loadOwnedEmployee(Long id) {
        Employee employee = employeeRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        authz.assertBusinessOwnership(employee);
        return employee;
    }

    private Employee.EmploymentStatus parseEmploymentStatus(String value) {
        try {
            return Employee.EmploymentStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown employment status: " + value + " (valid: ACTIVE, INACTIVE, TERMINATED)");
        }
    }

    private EmployeeResponse toResponse(Employee employee) {
        EmployeeResponse response = new EmployeeResponse();
        response.setId(employee.getId());
        response.setUserId(employee.getUserId());
        response.setFirstName(employee.getFirstName());
        response.setLastName(employee.getLastName());
        response.setEmail(employee.getEmail());
        response.setPhoneNumber(employee.getPhoneNumber());
        response.setEmploymentStatus(employee.getEmploymentStatus().name());
        response.setEmploymentStartDate(employee.getEmploymentStartDate());
        return response;
    }

    private CommissionRuleResponse toResponse(CommissionRule rule) {
        CommissionRuleResponse response = new CommissionRuleResponse();
        response.setId(rule.getId());
        response.setEmployeeId(rule.getEmployeeId());
        response.setPercentageRate(rule.getPercentageRate());
        response.setActive(rule.isActive());
        response.setEffectiveFrom(rule.getEffectiveFrom());
        response.setEffectiveTo(rule.getEffectiveTo());
        return response;
    }
}
