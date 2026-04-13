package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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
import es.triana.company.banking.service.exception.AccountValidationException;
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
        account.setTenantId(1L);
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        AccountDTO result = accountsService.getAccountById(1L, 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testGetAccountByIdNotFound() {
        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.getAccountById(1L, 1L));
    }

    @Test
    void testGetAccountByIdWrongTenant() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(TenantMismatchException.class, () -> accountsService.getAccountById(1L, 2L));
    }

    @Test
    void testGetAccountByIdNullId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.getAccountById(null, 1L));
        
        assertEquals("Account id is required", exception.getMessage());
    }

    @Test
    void testGetAccountByIdNullTenantId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.getAccountById(1L, null));
        
        assertEquals("Tenant id is required", exception.getMessage());
    }

    @Test
    void testCreateAccount() {
        Account account = new Account();
        account.setTenantId(1L);
        account.setIban("ES7620770024003102575766");
        AccountType accountType = AccountType.builder().id(1L).name("CHECKING").build();
        AccountDTO accountDTO = AccountDTO.builder().accountTypeId(1L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, "ES7620770024003102575766")).thenReturn(false);
        when(accountTypeRepository.findById(1L)).thenReturn(Optional.of(accountType));
        when(accountsRepository.save(account)).thenReturn(account);
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        AccountDTO result = accountsService.createAccount(accountDTO, 1L);

        assertNotNull(result);
        assertEquals(accountType, account.getAccountType());
        verify(accountsRepository, times(1)).save(account);
    }

    @Test
    void testDeleteAccount() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));

        accountsService.deleteAccount(1L, 1L);

        verify(accountsRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteAccountNotFound() {
        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.deleteAccount(1L, 1L));
    }

    @Test
    void testDeleteAccountWrongTenant() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(TenantMismatchException.class, () -> accountsService.deleteAccount(1L, 2L));
    }

    @Test
    void testDeleteAccountNullId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.deleteAccount(null, 1L));
        
        assertEquals("Account id is required", exception.getMessage());
    }

    @Test
    void testDeleteAccountNullTenantId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.deleteAccount(1L, null));
        
        assertEquals("Tenant id is required", exception.getMessage());
    }

    @Test
    void testUpdateAccount() {
        Account existingAccount = new Account();
        existingAccount.setId(1L);
        existingAccount.setTenantId(1L);
        AccountType accountType = AccountType.builder().id(2L).name("SAVINGS").build();
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).accountTypeId(2L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.existsByTenantIdAndIbanAndIdNot(1L, null, 1L)).thenReturn(false);
        when(accountTypeRepository.findById(2L)).thenReturn(Optional.of(accountType));
        when(accountsRepository.save(existingAccount)).thenReturn(existingAccount);
        when(accountMapper.toDto(existingAccount)).thenReturn(updatedAccountDTO);

        AccountDTO result = accountsService.updateAccount(updatedAccountDTO, 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(accountType, existingAccount.getAccountType());
        verify(accountsRepository, times(1)).save(existingAccount);
    }

    @Test
    void testUpdateAccountNotFound() {
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).accountTypeId(1L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.updateAccount(updatedAccountDTO, 1L));
    }

    @Test
    void testCreateAccountDuplicateIban() {
        Account account = new Account();
        account.setTenantId(1L);
        account.setIban("ES7620770024003102575766");
        AccountDTO accountDTO = AccountDTO.builder().accountTypeId(1L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, "ES7620770024003102575766")).thenReturn(true);

        assertThrows(DuplicateAccountIbanException.class, () -> accountsService.createAccount(accountDTO, 1L));
    }

    @Test
    void testUpdateAccountRejectsTenantChange() {
        Account existingAccount = new Account();
        existingAccount.setId(1L);
        existingAccount.setTenantId(1L);
        AccountDTO updatedAccountDTO = AccountDTO.builder().id(1L).accountTypeId(1L).currency("EUR").name("Updated Account").build();

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(existingAccount));

        assertThrows(TenantMismatchException.class, () -> accountsService.updateAccount(updatedAccountDTO, 2L));
    }

    @Test
    void testCreateAccountWithUnknownAccountType() {
        Account account = new Account();
        account.setTenantId(1L);
        AccountDTO accountDTO = AccountDTO.builder().accountTypeId(99L).currency("EUR").name("Main account").build();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.existsByTenantIdAndIban(1L, null)).thenReturn(false);
        when(accountTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountTypeNotFoundException.class, () -> accountsService.createAccount(accountDTO, 1L));
    }

    @Test
    void testUpdateAccountBalanceWithPositiveDelta() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);
        account.setLastBalanceReal(new BigDecimal("100.00"));

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountsRepository.save(account)).thenReturn(account);

        accountsService.updateAccountBalance(1L, 1L, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getLastBalanceReal());
        assertNotNull(account.getLastBalanceRealDate());
        assertNotNull(account.getUpdatedAt());
        verify(accountsRepository, times(1)).save(account);
    }

    @Test
    void testUpdateAccountBalanceWithNegativeDelta() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);
        account.setLastBalanceReal(new BigDecimal("100.00"));

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountsRepository.save(account)).thenReturn(account);

        accountsService.updateAccountBalance(1L, 1L, new BigDecimal("-30.00"));

        assertEquals(new BigDecimal("70.00"), account.getLastBalanceReal());
        assertNotNull(account.getLastBalanceRealDate());
        assertNotNull(account.getUpdatedAt());
        verify(accountsRepository, times(1)).save(account);
    }

    @Test
    void testUpdateAccountBalanceWithNullInitialBalance() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);
        account.setLastBalanceReal(null);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountsRepository.save(account)).thenReturn(account);

        accountsService.updateAccountBalance(1L, 1L, new BigDecimal("25.00"));

        assertEquals(new BigDecimal("25.00"), account.getLastBalanceReal());
        assertNotNull(account.getLastBalanceRealDate());
        assertNotNull(account.getUpdatedAt());
        verify(accountsRepository, times(1)).save(account);
    }

    @Test
    void testUpdateAccountBalanceAccountNotFound() {
        when(accountsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, 
            () -> accountsService.updateAccountBalance(99L, 1L, new BigDecimal("50.00")));
    }

    @Test
    void testUpdateAccountBalanceWrongTenant() {
        Account account = new Account();
        account.setId(1L);
        account.setTenantId(1L);
        account.setLastBalanceReal(new BigDecimal("100.00"));

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(AccountNotFoundException.class, 
            () -> accountsService.updateAccountBalance(1L, 2L, new BigDecimal("50.00")));
    }

    @Test
    void testUpdateAccountBalanceNullAccountId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.updateAccountBalance(null, 1L, new BigDecimal("50.00")));
        
        assertEquals("Account id is required", exception.getMessage());
    }

    @Test
    void testUpdateAccountBalanceNullTenantId() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.updateAccountBalance(1L, null, new BigDecimal("50.00")));
        
        assertEquals("Tenant id is required", exception.getMessage());
    }

    @Test
    void testUpdateAccountBalanceNullAmountDelta() {
        AccountValidationException exception = assertThrows(AccountValidationException.class, 
            () -> accountsService.updateAccountBalance(1L, 1L, null));
        
        assertEquals("Amount delta is required", exception.getMessage());
    }
}