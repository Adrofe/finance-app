package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.AccountType;
import es.triana.company.banking.repository.AccountTypeRepository;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.service.exception.AccountTypeNotFoundException;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.exception.DuplicateAccountIbanException;
import es.triana.company.banking.service.exception.TenantMismatchException;
import es.triana.company.banking.service.mapper.AccountMapper;

class AccountsServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private AccountTypeRepository accountTypeRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountsService accountsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllAccounts() {
        Account account = new Account();
        account.setId(1L);
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setId(1L);

        when(accountsRepository.findAll()).thenReturn(List.of(account));
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        List<AccountDTO> result = accountsService.getAccountsByTenant(null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void testGetAccountById() {
        Account account = new Account();
        account.setId(1L);
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        AccountDTO result = accountsService.getAccountById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetAccountByIdNotFound() {
        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.getAccountById(1L));
    }

    @Test
    void testCreateAccount() {
        Account account = new Account();
        account.setTenantId(1L);
        account.setIban("ES7620770024003102575766");
        AccountType accountType = AccountType.builder().id(1L).name("CHECKING").build();
        AccountDTO accountDTO = AccountDTO.builder().tenantId(1L).accountTypeId(1L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, "ES7620770024003102575766")).thenReturn(false);
        when(accountTypeRepository.findById(1L)).thenReturn(Optional.of(accountType));
        when(accountsRepository.save(account)).thenReturn(account);
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        AccountDTO result = accountsService.createAccount(accountDTO);

        assertNotNull(result);
        assertEquals(accountType, account.getAccountType());
        verify(accountsRepository, times(1)).save(account);
    }

    @Test
    void testDeleteAccount() {
        when(accountsRepository.existsById(1L)).thenReturn(true);

        accountsService.deleteAccount(1L);

        verify(accountsRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteAccountNotFound() {
        when(accountsRepository.existsById(1L)).thenReturn(false);

        assertThrows(AccountNotFoundException.class, () -> accountsService.deleteAccount(1L));
    }

    @Test
    void testUpdateAccount() {
        Account existingAccount = new Account();
        existingAccount.setId(1L);
        existingAccount.setTenantId(1L);
        AccountType accountType = AccountType.builder().id(2L).name("SAVINGS").build();
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).tenantId(1L).accountTypeId(2L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.existsByTenantIdAndIbanAndIdNot(1L, null, 1L)).thenReturn(false);
        when(accountTypeRepository.findById(2L)).thenReturn(Optional.of(accountType));
        when(accountsRepository.save(existingAccount)).thenReturn(existingAccount);
        when(accountMapper.toDto(existingAccount)).thenReturn(updatedAccountDTO);

        AccountDTO result = accountsService.updateAccount(updatedAccountDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(accountType, existingAccount.getAccountType());
        verify(accountsRepository, times(1)).save(existingAccount);
    }

    @Test
    void testUpdateAccountNotFound() {
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).tenantId(1L).accountTypeId(1L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.updateAccount(updatedAccountDTO));
    }

    @Test
    void testCreateAccountDuplicateIban() {
        Account account = new Account();
        account.setTenantId(1L);
        account.setIban("ES7620770024003102575766");
        AccountDTO accountDTO = AccountDTO.builder().tenantId(1L).accountTypeId(1L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, "ES7620770024003102575766")).thenReturn(true);

        assertThrows(DuplicateAccountIbanException.class, () -> accountsService.createAccount(accountDTO));
    }

    @Test
    void testUpdateAccountRejectsTenantChange() {
        Account existingAccount = new Account();
        existingAccount.setId(1L);
        existingAccount.setTenantId(1L);
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).tenantId(2L).accountTypeId(1L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(existingAccount));

        assertThrows(TenantMismatchException.class, () -> accountsService.updateAccount(updatedAccountDTO));
    }

    @Test
    void testCreateAccountWithUnknownAccountType() {
        Account account = new Account();
        account.setTenantId(1L);
        AccountDTO accountDTO = AccountDTO.builder().tenantId(1L).accountTypeId(99L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, null)).thenReturn(false);
        when(accountTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountTypeNotFoundException.class, () -> accountsService.createAccount(accountDTO));
    }
}