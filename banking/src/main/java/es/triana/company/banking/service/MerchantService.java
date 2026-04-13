package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.MerchantDTO;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.service.mapper.MerchantMapper;

@Service
public class MerchantService {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantMapper merchantMapper;

    public List<MerchantDTO> getAllMerchants() {
        return merchantRepository.findAll()
                .stream()
                .map(merchantMapper::toDto)
                .toList();
    }

    public MerchantDTO getMerchantById(Long id) {
        return merchantRepository.findById(id)
                .map(merchantMapper::toDto)
                .orElse(null);
    }
}
