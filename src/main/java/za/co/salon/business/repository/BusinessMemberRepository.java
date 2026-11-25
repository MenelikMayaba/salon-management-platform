package za.co.salon.business.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.business.entity.BusinessMember;

import java.util.List;
import java.util.Optional;

public interface BusinessMemberRepository extends JpaRepository<BusinessMember, Long> {

    Optional<BusinessMember> findByUserIdAndBusinessId(Long userId, Long businessId);

    List<BusinessMember> findByUserIdAndStatus(Long userId, BusinessMember.Status status);

    List<BusinessMember> findByBusinessId(Long businessId);

    boolean existsByUserIdAndBusinessId(Long userId, Long businessId);
}
