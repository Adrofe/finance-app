package es.triana.company.banking.service;

import java.util.List;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.AccountDTO;

@Service
public class AccountsService {

    public List<AccountDTO> getAccountsByTenant(String tenantId) {
        if (tenantId == null) {
            return getAllAccounts();
        } else {
            return getAccountsForTenant(tenantId);
        }
    }

    private List<AccountDTO> getAllAccounts() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private List<AccountDTO> getAccountsForTenant(String tenantId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AccountDTO createAccount(AccountDTO account) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteAccount(Long id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteAccount'");
    }

    public AccountDTO getAccountById(Long id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAccountById'");
    }

    public AccountDTO updateAccount(AccountDTO updatedAccount) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAccount'");
    }

}
