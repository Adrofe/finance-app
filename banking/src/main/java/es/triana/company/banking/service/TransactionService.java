package es.triana.company.banking.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionDTO;

@Service
public class TransactionService {

    public Object createTransaction(TransactionDTO transactionDTO, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createTransaction'");
    }

    public Object getTransactionById(Long transactionId, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionById'");
    }

    public Object getTransactionsByAccount(Long accountId, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionsByAccount'");
    }

    public Object getTransactionsByTenant(Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionsByTenant'");
    }

    public Object getTransactionsByDateRange(LocalDate startDate, LocalDate endDate, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionsByDateRange'");
    }

    public Object updateTransaction(Long transactionId, TransactionDTO transactionDTO, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTransaction'");
    }

    public void deleteTransaction(Long transactionId, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteTransaction'");
    }

    public Object getAccountBalance(Long accountId, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAccountBalance'");
    }

    public Object getTenantBalance(Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTenantBalance'");
    }

    public Object getTransactionsByCategory(Long categoryId, Long tenantId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionsByCategory'");
    }
    
}
