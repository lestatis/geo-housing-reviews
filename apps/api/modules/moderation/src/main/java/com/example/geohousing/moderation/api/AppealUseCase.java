package com.example.geohousing.moderation.api;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.ModeratorId;

/**
 * Hearing an appeal.
 *
 * <p>An overturn reverses the decision on the content and then records the outcome. Split across
 * transactions, a stale appeal refused at the second step leaves the content restored and the
 * appeal still marked upheld — the author gets their review back and the record says they lost.
 */
public interface AppealUseCase {

  java.util.List<com.example.geohousing.moderation.application.PendingAppeal> pending();

  Appeal uphold(AppealId appealId, ModeratorId moderatorId, String explanation);

  Appeal overturn(AppealId appealId, ModeratorId moderatorId, String explanation);
}
