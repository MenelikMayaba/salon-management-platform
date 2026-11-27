package za.co.salon.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.catalogue.entity.CommissionRule;

import java.util.List;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {

    List<CommissionRule> findAllByEmployeeIdAndActiveTrue(Long employeeId);
}
