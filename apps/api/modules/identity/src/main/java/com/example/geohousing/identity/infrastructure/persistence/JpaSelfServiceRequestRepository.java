package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.SelfServiceRequestRepository;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.SelfServiceRequest;
import com.example.geohousing.identity.domain.SelfServiceRequestAlreadyExistsException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the self-service request (idempotency) log. */
@Repository
public class JpaSelfServiceRequestRepository implements SelfServiceRequestRepository {

  private static final String UNIQUE_KEY_CONSTRAINT =
      "self_service_request_account_id_idempotency_key_key";

  private final SpringDataSelfServiceRequestRepository requests;

  public JpaSelfServiceRequestRepository(SpringDataSelfServiceRequestRepository requests) {
    this.requests = requests;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SelfServiceRequest> find(AccountId accountId, String idempotencyKey) {
    return requests
        .findByAccountIdAndIdempotencyKey(accountId.value(), idempotencyKey)
        .map(SelfServiceRequestJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void record(SelfServiceRequest request) {
    try {
      requests.saveAndFlush(SelfServiceRequestJpaMapper.toEntity(request));
    } catch (DataIntegrityViolationException exception) {
      if (isUniqueKeyViolation(exception)) {
        throw new SelfServiceRequestAlreadyExistsException(
            "a self-service request already exists for this account and idempotency key");
      }
      throw exception;
    }
  }

  private static boolean isUniqueKeyViolation(DataIntegrityViolationException exception) {
    return exception.getCause() instanceof ConstraintViolationException violation
        && UNIQUE_KEY_CONSTRAINT.equals(violation.getConstraintName());
  }
}
