package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionTypeDTO;
import es.triana.company.banking.repository.TransactionTypeRepository;
import es.triana.company.banking.service.mapper.TransactionTypeMapper;

@Service
public class TransactionTypeService {

    @Autowired
    private TransactionTypeRepository transactionTypeRepository;

    @Autowired
    private TransactionTypeMapper transactionTypeMapper;

    public List<TransactionTypeDTO> getAllTypes() {
        return transactionTypeRepository.findAll()
                .stream()
                .map(transactionTypeMapper::toDto)
                .toList();
    }

    public TransactionTypeDTO getTypeById(Long id) {
        return transactionTypeRepository.findById(id)
                .map(transactionTypeMapper::toDto)
                .orElse(null);
    }

    public TransactionTypeDTO getTypeByName(String name) {
        return transactionTypeRepository.findByName(name)
                .map(transactionTypeMapper::toDto)
                .orElse(null);
    }
}
