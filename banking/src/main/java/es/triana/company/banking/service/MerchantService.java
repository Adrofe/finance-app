package es.triana.company.banking.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.MerchantDTO;
import es.triana.company.banking.model.db.Category;
import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.repository.CategoryRepository;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.exception.TransactionValidationException;
import es.triana.company.banking.service.mapper.MerchantMapper;

@Service
public class MerchantService {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private TransactionRepository transactionRepository;

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

    public MerchantDTO createMerchant(MerchantDTO merchantDTO) {
        var merchant = merchantMapper.toEntity(merchantDTO);
        if (merchantDTO.getCategoryId() != null) {
            Category category = categoryRepository.findById(merchantDTO.getCategoryId())
                    .orElseThrow(() -> new TransactionValidationException("Category not found with id: " + merchantDTO.getCategoryId()));
            merchant.setCategory(category);
        }
        var saved = merchantRepository.save(merchant);
        return merchantMapper.toDto(saved);
    }

    public MerchantDTO updateMerchant(Long id, MerchantDTO merchantDTO) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new TransactionValidationException("Merchant not found with id: " + id));

        if (merchantDTO.getName() != null && !merchantDTO.getName().trim().isEmpty()) {
            merchantRepository.findByName(merchantDTO.getName()).ifPresent(m -> {
                if (!m.getId().equals(id)) {
                    throw new TransactionValidationException("Merchant name already exists: " + merchantDTO.getName());
                }
            });
            merchant.setName(merchantDTO.getName().trim());
        }

        if (merchantDTO.getCategoryId() != null) {
            Category category = categoryRepository.findById(merchantDTO.getCategoryId())
                    .orElseThrow(() -> new TransactionValidationException("Category not found with id: " + merchantDTO.getCategoryId()));
            merchant.setCategory(category);
        } else {
            merchant.setCategory(null);
        }

        merchant.setUpdatedAt(OffsetDateTime.now());
        var saved = merchantRepository.save(merchant);
        return merchantMapper.toDto(saved);
    }

    public void deleteMerchant(Long id) {
        if (!merchantRepository.existsById(id)) {
            throw new TransactionValidationException("Merchant not found with id: " + id);
        }
        transactionRepository.clearMerchantReferences(id);
        merchantRepository.deleteById(id);
    }
}
