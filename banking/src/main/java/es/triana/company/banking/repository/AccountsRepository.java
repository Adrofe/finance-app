package es.triana.company.banking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.Account;

public interface AccountsRepository extends JpaRepository<Account, Long> {

    List<Account> findByTenantId(long long1);
    
}
