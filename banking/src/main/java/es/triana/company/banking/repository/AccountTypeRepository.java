package es.triana.company.banking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.AccountType;

public interface AccountTypeRepository extends JpaRepository<AccountType, Long> {
}