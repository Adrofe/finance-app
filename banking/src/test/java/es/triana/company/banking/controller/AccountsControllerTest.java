package es.triana.company.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAllsAccountsByTenantId() {
        List<AccountDTO> tenant1Accounts = List.of(new AccountDTO(1L, "Account A", 1000.0, 1));
        when(accountsService.getAccountsByTenant("1")).thenReturn(tenant1Accounts);

        ResponseEntity<ApiResponse<List<AccountDTO>>> response = accountsController.getAllAccounts("1");

        verify(accountsService).getAccountsByTenant("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Account A", response.getBody().getData().get(0).getName());
    }

    @Test
    public void testGetAllsAccounts(){
        List<AccountDTO> mockAccounts = List.of(new AccountDTO(1L, "Account A", 1000.0, 1),
                                               new AccountDTO(2L, "Account B", 2000.0, 2));
        when(accountsService.getAccountsByTenant(null)).thenReturn(mockAccounts);

        ResponseEntity<ApiResponse<List<AccountDTO>>> response = accountsController.getAllAccounts(null);
        verify(accountsService).getAccountsByTenant(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getData().size());
    }

    @Test
    public void testCreateAccount(){
        AccountDTO newAccount = new AccountDTO(null, "New Account", 500.0, 1);

        when(accountsService.createAccount(newAccount)).thenReturn(new AccountDTO(3L, "New Account", 500.0, 1));

        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.createAccount(newAccount);

        verify(accountsService).createAccount(newAccount);
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

        ResponseEntity<ApiResponse<Void>> response = accountsController.deleteAccount(id);

        verify(accountsService).deleteAccount(id);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Account not found with id: " + id, response.getBody().getMessage());
    }

    @Test
    public void testGetAccountById(){
        Long id = 1L;
        AccountDTO account = new AccountDTO(id, "Account A", 1000.0, 1);

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
        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.getAccountById(id);

        verify(accountsService).getAccountById(id);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Account not found with id: " + id, response.getBody().getMessage());
    }

    @Test
    public void testUpdateAccount(){
        AccountDTO updatedAccount = new AccountDTO(1L, "Updated Account", 1500.0, 1);

        when(accountsService.updateAccount(updatedAccount)).thenReturn(updatedAccount);

        ResponseEntity<ApiResponse<AccountDTO>> response = accountsController.updateAccount(updatedAccount);

        verify(accountsService).updateAccount(updatedAccount);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Updated Account", response.getBody().getData().getName());
    }
}
