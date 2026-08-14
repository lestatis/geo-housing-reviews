package com.example.geohousing.moderation.infrastructure;

import com.example.geohousing.moderation.api.AppealUseCase;
import com.example.geohousing.moderation.application.AppealService;
import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.ModeratorId;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction boundary for hearing an appeal.
 *
 * <p>An overturn restores the content and then records the outcome. Separately committed, a stale
 * appeal refused at the second step left the review restored and the appeal marked upheld: the
 * author has their content back and the record says they lost, which is the worst of both.
 */
public class TransactionalAppealService implements AppealUseCase {

  private final AppealService delegate;

  public TransactionalAppealService(AppealService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<com.example.geohousing.moderation.application.PendingAppeal> pending() {
    return delegate.pending();
  }

  @Override
  @Transactional
  public Appeal uphold(AppealId appealId, ModeratorId moderatorId, String explanation) {
    return delegate.uphold(appealId, moderatorId, explanation);
  }

  @Override
  @Transactional
  public Appeal overturn(AppealId appealId, ModeratorId moderatorId, String explanation) {
    return delegate.overturn(appealId, moderatorId, explanation);
  }
}
