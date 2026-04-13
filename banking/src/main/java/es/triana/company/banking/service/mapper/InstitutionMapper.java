package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.InstitutionDTO;
import es.triana.company.banking.model.db.Institution;

@Component
public class InstitutionMapper {

    public InstitutionDTO toDto(Institution institution) {
        if (institution == null) {
            return null;
        }
        return InstitutionDTO.builder()
                .id(institution.getId())
                .name(institution.getName())
                .country(institution.getCountry())
                .website(institution.getWebsite())
                .build();
    }

    public Institution toEntity(InstitutionDTO institutionDTO) {
        if (institutionDTO == null) {
            return null;
        }
        return Institution.builder()
                .id(institutionDTO.getId())
                .name(institutionDTO.getName())
                .country(institutionDTO.getCountry())
                .website(institutionDTO.getWebsite())
                .build();
    }
}
