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
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.mapper.AccountMapper;

class AccountsServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

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
        AccountDTO accountDTO = new AccountDTO();

        when(accountMapper.toEntity(accountDTO)).thenReturn(account);
        when(accountsRepository.save(account)).thenReturn(account);
        when(accountMapper.toDto(account)).thenReturn(accountDTO);

        AccountDTO result = accountsService.createAccount(accountDTO);

        assertNotNull(result);
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
        AccountDTO updatedAccountDTO = new AccountDTO();
        updatedAccountDTO.setId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.save(existingAccount)).thenReturn(existingAccount);
        when(accountMapper.toDto(existingAccount)).thenReturn(updatedAccountDTO);

        AccountDTO result = accountsService.updateAccount(updatedAccountDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(accountsRepository, times(1)).save(existingAccount);
    }

    @Test
    void testUpdateAccountNotFound() {
        AccountDTO updatedAccountDTO = new AccountDTO();
        updatedAccountDTO.setId(1L);

        when(accountsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountsService.updateAccount(updatedAccountDTO));
    }
}