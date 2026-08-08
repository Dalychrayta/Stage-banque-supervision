package com.bct.collector.repository;

import com.bct.collector.model.LogEntry;
import com.bct.collector.model.LogLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    List<LogEntry> findByResourceIdOrderByLogTimestampDesc(String resourceId);

    List<LogEntry> findByResourceIdAndLevelInAndLogTimestampBetweenOrderByLogTimestampDesc(
            String resourceId, List<LogLevel> levels, LocalDateTime from, LocalDateTime to);

    List<LogEntry> findByLevelAndLogTimestampAfterOrderByLogTimestampDesc(
            LogLevel level, LocalDateTime after);
}
