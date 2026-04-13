package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.AccountTypeDTO;
import es.triana.company.banking.repository.AccountTypeRepository;
import es.triana.company.banking.service.mapper.AccountTypeMapper;

@Service
public class AccountTypeService {

    @Autowired
    private AccountTypeRepository accountTypeRepository;

    @Autowired
    private AccountTypeMapper accountTypeMapper;

    public List<AccountTypeDTO> getAllAccountTypes() {
        return accountTypeRepository.findAll()
                .stream()
                .map(accountTypeMapper::toDto)
                .toList();
    }

    public AccountTypeDTO getAccountTypeById(Long id) {
        return accountTypeRepository.findById(id)
                .map(accountTypeMapper::toDto)
                .orElse(null);
    }
}
