package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.AccountDeletionRepository;
import com.example.geohousing.identity.application.AccountRepository;
import com.example.geohousing.identity.application.IdentityProvisioningRepository;
import com.example.geohousing.identity.application.PublicProfileRepository;
import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import com.example.geohousing.identity.domain.AuthSubjectAlreadyProvisionedException;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of identity's application persistence ports. */
@Repository
public class JpaIdentityPersistenceAdapter
    implements AccountRepository,
        PublicProfileRepository,
        IdentityProvisioningRepository,
        AccountDeletionRepository {

  /** Renamed from its Postgres-generated name by V2.4 to match the column it now constrains. */
  private static final String AUTH_SUBJECT_HASH_UNIQUE_CONSTRAINT = "account_auth_subject_hash_key";

  private static final String PSEUDONYM_UNIQUE_CONSTRAINT = "public_profile_pseudonym_key";

  private final SpringDataAccountRepository accountRepository;
  private final SpringDataPublicProfileRepository publicProfileRepository;

  public JpaIdentityPersistenceAdapter(
      SpringDataAccountRepository accountRepository,
      SpringDataPublicProfileRepository publicProfileRepository) {
    this.accountRepository = accountRepository;
    this.publicProfileRepository = publicProfileRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Account> findById(AccountId accountId) {
    return accountRepository.findById(accountId.value()).map(AccountJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
    return accountRepository.findByAuthSubjectHash(authSubjectHash).map(AccountJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void create(Account account, PublicProfile profile) {
    if (!account.id().equals(profile.accountId())) {
      throw new IllegalArgumentException("account and profile must have the same account id");
    }
    try {
      // Flushed rather than left to commit-time, so a unique violation is raised here where it can
      // still be translated instead of escaping the port as an infrastructure exception.
      accountRepository.saveAndFlush(AccountJpaMapper.toEntity(account));
      publicProfileRepository.saveAndFlush(PublicProfileJpaMapper.toEntity(profile));
    } catch (DataIntegrityViolationException exception) {
      throw translateUniqueViolation(exception, profile.pseudonym());
    }
  }

  @Override
  @Transactional
  public void lockActiveAdministrators() {
    // FOR UPDATE, so a concurrent role change waits here rather than deciding against a stale view
    // of who is left. Serializes role changes; at this volume that costs nothing, and it is the
    // only thing standing between two simultaneous demotions and an unreachable platform.
    accountRepository.lockActiveAdministrators();
  }

  @Override
  @Transactional
  public void lockAccount(AccountId accountId) {
    // NO KEY UPDATE for the same reason as the administrator set: user_restriction.account_id
    // references this table, so an insert here needs the key-share lock that a plain FOR UPDATE
    // would block — and the blocked insert would be one this very transaction is waiting to make.
    accountRepository.lockAccount(accountId.value());
  }

  @Override
  @Transactional
  public Account save(Account account, long expectedVersion) {
    AccountJpaEntity entity =
        accountRepository
            .findById(account.id().value())
            .orElseThrow(() -> new AccountNotFoundException(account.id()));
    if (entity.version() != expectedVersion) {
      throw new OptimisticLockConflictException("account version does not match");
    }

    entity.applyRole(account.role());
    try {
      return AccountJpaMapper.toDomain(accountRepository.saveAndFlush(entity));
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new OptimisticLockConflictException("account version does not match");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PublicProfile> findByPseudonym(Pseudonym pseudonym) {
    return publicProfileRepository
        .findByPseudonym(pseudonym.value())
        .map(PublicProfileJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public long countByRole(AccountRole role) {
    return accountRepository.countByRoleAndStatus(role, AccountStatus.ACTIVE);
  }

  @Override
  @Transactional
  public void applyDeletion(Account closedAccount, PublicProfile anonymizedProfile) {
    AccountJpaEntity account =
        accountRepository
            .findById(closedAccount.id().value())
            .orElseThrow(() -> new AccountNotFoundException(closedAccount.id()));
    account.applyClosure(
        closedAccount
            .closedAt()
            .orElseThrow(
                () -> new IllegalArgumentException("closed account must have a closedAt")));

    PublicProfileJpaEntity profile =
        publicProfileRepository
            .findById(anonymizedProfile.accountId().value())
            .orElseThrow(() -> new AccountNotFoundException("public profile not found"));
    PublicProfileJpaMapper.copyMutableFields(anonymizedProfile, profile);

    accountRepository.saveAndFlush(account);
    publicProfileRepository.saveAndFlush(profile);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PublicProfile> findByAccountId(AccountId accountId) {
    return publicProfileRepository
        .findById(accountId.value())
        .map(PublicProfileJpaMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isPseudonymInUse(Pseudonym pseudonym) {
    return publicProfileRepository.existsByPseudonym(pseudonym.value());
  }

  @Override
  @Transactional
  public PublicProfile save(PublicProfile profile, long expectedVersion) {
    PublicProfileJpaEntity entity =
        publicProfileRepository
            .findById(profile.accountId().value())
            .orElseThrow(() -> new AccountNotFoundException("public profile not found"));
    if (entity.version() != expectedVersion) {
      throw new OptimisticLockConflictException("public profile version does not match");
    }

    PublicProfileJpaMapper.copyMutableFields(profile, entity);
    try {
      return PublicProfileJpaMapper.toDomain(publicProfileRepository.saveAndFlush(entity));
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new OptimisticLockConflictException("public profile version does not match");
    } catch (DataIntegrityViolationException exception) {
      throw translateUniqueViolation(exception, profile.pseudonym());
    }
  }

  /**
   * Maps a violated unique constraint onto the domain conflict it actually represents. Matching on
   * the constraint name keeps an unrelated integrity failure (a NOT NULL or foreign-key breach,
   * say) from being reported to the user as "pseudonym already taken": anything unrecognised is
   * rethrown as-is rather than guessed at.
   */
  private static RuntimeException translateUniqueViolation(
      DataIntegrityViolationException exception, Pseudonym pseudonym) {
    String constraintName = constraintNameOf(exception);
    if (PSEUDONYM_UNIQUE_CONSTRAINT.equals(constraintName)) {
      return new PseudonymAlreadyInUseException(pseudonym);
    }
    if (AUTH_SUBJECT_HASH_UNIQUE_CONSTRAINT.equals(constraintName)) {
      return new AuthSubjectAlreadyProvisionedException(
          "an account for this auth subject was provisioned concurrently");
    }
    return exception;
  }

  private static String constraintNameOf(DataIntegrityViolationException exception) {
    return exception.getCause() instanceof ConstraintViolationException violation
        ? violation.getConstraintName()
        : null;
  }
}
