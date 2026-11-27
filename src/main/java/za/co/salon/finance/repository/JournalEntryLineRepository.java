package za.co.salon.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.salon.finance.entity.JournalEntryLine;

import java.util.List;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, Long> {

    List<JournalEntryLine> findAllByJournalEntryId(Long journalEntryId);
}
