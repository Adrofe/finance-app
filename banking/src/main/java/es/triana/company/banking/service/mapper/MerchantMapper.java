package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.MerchantDTO;
import es.triana.company.banking.model.db.Merchant;

@Component
public class MerchantMapper {

    public MerchantDTO toDto(Merchant merchant) {
        if (merchant == null) {
            return null;
        }
        return MerchantDTO.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .categoryId(merchant.getCategory() != null ? merchant.getCategory().getId() : null)
                .categoryName(merchant.getCategory() != null ? merchant.getCategory().getName() : null)
                .build();
    }

    public Merchant toEntity(MerchantDTO merchantDTO) {
        if (merchantDTO == null) {
            return null;
        }
        return Merchant.builder()
                .id(merchantDTO.getId())
                .name(merchantDTO.getName())
                .category(null) // Category is set separately in the service
                .build();
    }
}
