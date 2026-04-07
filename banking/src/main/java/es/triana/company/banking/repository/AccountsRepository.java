package es.triana.company.banking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.Account;

public interface AccountsRepository extends JpaRepository<Account, Long> {

    List<Account> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndIban(Long tenantId, String iban);

    boolean existsByTenantIdAndIbanAndIdNot(Long tenantId, String iban, Long id);
}
