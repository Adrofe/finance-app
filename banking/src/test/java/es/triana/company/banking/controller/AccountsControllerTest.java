package es.triana.company.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.AccountsService;
import es.triana.company.banking.service.exception.AccountNotFoundException;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class AccountsControllerTest {
    
    @InjectMocks
    private AccountsController accountsController;

    @Mock
    private AccountsService accountsService;

    @Mock
    private TenantContext tenantContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tenantContext.getCurrentTenantId()).thenReturn(1L);
    }

    @Test
    public void testGetAllsAccountsByTenantId() {
        List<AccountDTO> tenant1Accounts = List.of(AccountDTO.builder().id(1L).name("Account A").balance(1000.0).build());
        when(accountsService.getAccountsByTenant("1")).thenReturn(tenant1Accounts);

        ResponseEntity<ApiResponse<List<AccountDTO>>> response = accountsController.getAllAccounts();

        verify(accountsService).getAccountsByTenant("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Account A", response.getBody().getData().get(0).getName());
    }

    @Test
    public void testGetAllsAccounts(){
        List<AccountDTO> mockAccounts = List.of(AccountDTO.builder().id(1L).name("Account A").balance(1000.0).build(),
                                               AccountDTO.builder().id(2L).name("Account B").balance(2000.0).build());
        when(accountsService.getAccountsByTenant("1")).thenReturn(mockAccounts);

        ResponseEntity<ApiResponse<List<AccountDTO>>> response = accountsController.getAllAccounts();
        verify(accountsService).getAccountsByTenant("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getData().size());
    }

    @Test
    public void testCreateAccount(){
        AccountDTO newAccount = AccountDTO.builder().id(null).name("New Account").balance(500.0).build();

        when(accountsService.createAccount(newAccount, 1L)).thenReturn(AccountDTO.builder().id(3L).name("New Account").balance(500.0).build());

        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.createAccount(newAccount);

        verify(accountsService).createAccount(newAccount, 1L);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3L, response.getBody().getData().getId());
    }

    @Test
    public void testDeleteAccount(){
        Long id = 1L;
        ResponseEntity<ApiResponse<Void>> response = accountsController.deleteAccount(id);

        verify(accountsService).deleteAccount(id);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    public void testDeleteAccountNotFound(){
        Long id = 999L;
        doThrow(new AccountNotFoundException("Account not found with id: " + id)).when(accountsService).deleteAccount(id);

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
            () -> accountsController.deleteAccount(id));

        verify(accountsService).deleteAccount(id);
        assertEquals("Account not found with id: " + id, exception.getMessage());
    }

    @Test
    public void testGetAccountById(){
        Long id = 1L;
        AccountDTO account = AccountDTO.builder().id(id).name("Account A").balance(1000.0).build();

        when(accountsService.getAccountById(id)).thenReturn(account);

        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.getAccountById(id);

        verify(accountsService).getAccountById(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getData().getId());
    }

    @Test
    public void testGetAccountByIdNotFound(){
        Long id = 999L;
        when(accountsService.getAccountById(id)).thenThrow(new AccountNotFoundException("Account not found with id: " + id));

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                () -> accountsController.getAccountById(id));

        verify(accountsService).getAccountById(id);
        assertEquals("Account not found with id: " + id, exception.getMessage());
    }

    @Test
    public void testUpdateAccount(){
        AccountDTO updatedAccount = AccountDTO.builder().id(1L).name("Updated Account").balance(1500.0).build();

        when(accountsService.updateAccount(updatedAccount, 1L)).thenReturn(updatedAccount);

        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.updateAccount(1L, updatedAccount);

        verify(accountsService).updateAccount(updatedAccount, 1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Updated Account", response.getBody().getData().getName());
    }
}
