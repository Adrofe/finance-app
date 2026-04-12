package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import es.triana.company.banking.model.api.CsvImportRequest;
import es.triana.company.banking.model.api.CsvImportResult;
import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.exception.TransactionValidationException;

class CSVImporterServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private CSVImporterService csvImporterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldRejectMissingFile() {
        CsvImportRequest request = CsvImportRequest.builder()
                .accountId(ACCOUNT_ID)
                .build();

        TransactionValidationException exception = assertThrows(TransactionValidationException.class,
                () -> csvImporterService.importFile(request, TENANT_ID));

        assertEquals("CSV file is required", exception.getMessage());
    }

    @Test
    void shouldImportValidCsvRow() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                ("booking_date,value_date,amount,currency,merchant,merchant_id,description,external_id,destination_account_id,category_id,tag_ids,status_id,type_id\n"
                        + "2025-01-10,2025-01-10,99.50,EUR,Amazon,15,Prime subscription,EXT-100,30,7,1;3,2,4\n")
                        .getBytes());

        CsvImportRequest request = CsvImportRequest.builder()
                .file(file)
                .accountId(ACCOUNT_ID)
                .skipDuplicates(false)
                .build();

        when(accountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(Account.builder()
                .id(ACCOUNT_ID)
                .tenantId(TENANT_ID)
                .name("Main account")
                .build()));
        when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-100")).thenReturn(false);
        when(transactionService.createTransaction(any(TransactionDTO.class), eq(TENANT_ID))).thenReturn(TransactionDTO.builder().build());

        CsvImportResult result = csvImporterService.importFile(request, TENANT_ID);
        ArgumentCaptor<TransactionDTO> transactionCaptor = ArgumentCaptor.forClass(TransactionDTO.class);

        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(0, result.getSkippedCount());
        verify(transactionService, times(1)).createTransaction(transactionCaptor.capture(), eq(TENANT_ID));
        assertEquals(ACCOUNT_ID, transactionCaptor.getValue().getSourceAccountId());
        assertEquals(30L, transactionCaptor.getValue().getDestinationAccountId());
        assertEquals(15L, transactionCaptor.getValue().getMerchantId());
        assertEquals(7L, transactionCaptor.getValue().getCategoryId());
        assertEquals(2L, transactionCaptor.getValue().getStatusId());
        assertEquals(4L, transactionCaptor.getValue().getTypeId());
        assertEquals(java.util.List.of(1L, 3L), transactionCaptor.getValue().getTagIds());
    }

    @Test
    void shouldRejectInvalidOptionalNumericColumns() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                ("booking_date,amount,currency,merchant,description,destination_account_id,category_id,tag_ids,status_id,type_id\n"
                        + "2025-01-10,99.50,EUR,Amazon,Prime subscription,abc,x,1;bad,foo,bar\n")
                        .getBytes());

        CsvImportRequest request = CsvImportRequest.builder()
                .file(file)
                .accountId(ACCOUNT_ID)
                .skipDuplicates(false)
                .build();

        when(accountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(Account.builder()
                .id(ACCOUNT_ID)
                .tenantId(TENANT_ID)
                .name("Main account")
                .build()));

        CsvImportResult result = csvImporterService.importFile(request, TENANT_ID);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(5, result.getFailedCount());
        verify(transactionService, never()).createTransaction(any(TransactionDTO.class), eq(TENANT_ID));
    }

    @Test
    void shouldSkipExistingDuplicateWhenRequested() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                ("booking_date,amount,currency,merchant,description,external_id\n"
                        + "2025-01-10,99.50,EUR,Amazon,Prime subscription,EXT-100\n")
                        .getBytes());

        CsvImportRequest request = CsvImportRequest.builder()
                .file(file)
                .accountId(ACCOUNT_ID)
                .skipDuplicates(true)
                .build();

        when(accountsRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(Account.builder()
                .id(ACCOUNT_ID)
                .tenantId(TENANT_ID)
                .name("Main account")
                .build()));
        when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-100")).thenReturn(true);

        CsvImportResult result = csvImporterService.importFile(request, TENANT_ID);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getSkippedCount());
        verify(transactionService, never()).createTransaction(any(TransactionDTO.class), eq(TENANT_ID));
    }

    @Test
    void shouldImportUsingSourceAccountIdWhenAccountIdNotProvided() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                ("source_account_id,booking_date,amount,currency,merchant,description,external_id\n"
                        + "99,2025-01-10,99.50,EUR,Amazon,Prime subscription,EXT-200\n")
                                .getBytes());

        CsvImportRequest request = CsvImportRequest.builder()
                .file(file)
                .skipDuplicates(false)
                .build();

        when(accountsRepository.findById(99L)).thenReturn(Optional.of(Account.builder()
                .id(99L)
                .tenantId(TENANT_ID)
                .name("CSV account")
                .build()));
        when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-200")).thenReturn(false);
        when(transactionService.createTransaction(any(TransactionDTO.class), eq(TENANT_ID)))
                .thenReturn(TransactionDTO.builder().build());

        CsvImportResult result = csvImporterService.importFile(request, TENANT_ID);
        ArgumentCaptor<TransactionDTO> transactionCaptor = ArgumentCaptor.forClass(TransactionDTO.class);

        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        verify(transactionService, times(1)).createTransaction(transactionCaptor.capture(), eq(TENANT_ID));
        assertEquals(99L, transactionCaptor.getValue().getSourceAccountId());
    }

    @Test
    void shouldRejectRowWithoutSourceAccountIdWhenAccountIdNotProvided() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                ("booking_date,amount,currency,merchant,description\n"
                        + "2025-01-10,99.50,EUR,Amazon,Prime subscription\n")
                                .getBytes());

        CsvImportRequest request = CsvImportRequest.builder()
                .file(file)
                .skipDuplicates(false)
                .build();

        CsvImportResult result = csvImporterService.importFile(request, TENANT_ID);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        verify(transactionService, never()).createTransaction(any(TransactionDTO.class), eq(TENANT_ID));
    }
}