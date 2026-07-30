package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportId;
import com.example.geohousing.moderation.domain.ReporterId;
import java.util.List;
import java.util.Optional;

/** Persistence port for reports. */
public interface ReportRepository {

  /**
   * The reporter's live report about this target, if they have one. Backs the rule that an account
   * may raise an issue rather than raise it fifty times; the database enforces the same thing with
   * a partial unique index.
   */
  Optional<Report> findLive(ReporterId reporterId, ModerationTargetRef target);

  Optional<Report> findById(ReportId reportId);

  /** Reports attached to a case, so closing the case can close them out too. */
  List<Report> findByCase(ModerationCaseId caseId);

  void create(Report report);

  void save(Report report);
}
