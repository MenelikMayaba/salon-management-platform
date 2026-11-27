package za.co.salon.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.finance.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByBusinessId(Long businessId);

    Optional<Account> findByBusinessIdAndAccountCode(Long businessId, String accountCode);
}
