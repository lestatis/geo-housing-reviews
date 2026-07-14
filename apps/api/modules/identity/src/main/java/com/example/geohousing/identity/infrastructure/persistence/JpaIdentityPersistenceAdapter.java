package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.AccountRepository;
import com.example.geohousing.identity.application.IdentityProvisioningRepository;
import com.example.geohousing.identity.application.PublicProfileRepository;
import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of identity's application persistence ports. */
@Repository
public class JpaIdentityPersistenceAdapter
    implements AccountRepository, PublicProfileRepository, IdentityProvisioningRepository {

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
    accountRepository.save(AccountJpaMapper.toEntity(account));
    publicProfileRepository.save(PublicProfileJpaMapper.toEntity(profile));
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
      throw new PseudonymAlreadyInUseException(profile.pseudonym());
    }
  }
}
