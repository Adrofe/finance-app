package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionStatusDTO;
import es.triana.company.banking.repository.TransactionStatusRepository;
import es.triana.company.banking.service.mapper.TransactionStatusMapper;

@Service
public class TransactionStatusService {

    @Autowired
    private TransactionStatusRepository transactionStatusRepository;

    @Autowired
    private TransactionStatusMapper transactionStatusMapper;

    public List<TransactionStatusDTO> getAllStatuses() {
        return transactionStatusRepository.findAll()
                .stream()
                .map(transactionStatusMapper::toDto)
                .toList();
    }

    public TransactionStatusDTO getStatusById(Long id) {
        return transactionStatusRepository.findById(id)
                .map(transactionStatusMapper::toDto)
                .orElse(null);
    }

    public TransactionStatusDTO getStatusByCode(String code) {
        return transactionStatusRepository.findByCode(code)
                .map(transactionStatusMapper::toDto)
                .orElse(null);
    }
}
