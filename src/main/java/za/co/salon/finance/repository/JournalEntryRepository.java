package za.co.salon.finance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import za.co.salon.finance.entity.JournalEntry;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdAndBusinessId(Long id, Long businessId);

    Optional<JournalEntry> findByBusinessIdAndSourceTypeAndSourceId(Long businessId, String sourceType, Long sourceId);

    Page<JournalEntry> findByBusinessId(Long businessId, Pageable pageable);

    Page<JournalEntry> findByBusinessIdAndPostingDateBetween(Long businessId, LocalDate from, LocalDate to, Pageable pageable);
}
