package es.triana.company.banking.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.InstitutionDTO;
import es.triana.company.banking.repository.InstitutionRepository;
import es.triana.company.banking.service.mapper.InstitutionMapper;

@Service
public class InstitutionService {

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionMapper institutionMapper;

    public List<InstitutionDTO> getAllInstitutions() {
        return institutionRepository.findAll()
                .stream()
                .map(institutionMapper::toDto)
                .toList();
    }

    public InstitutionDTO getInstitutionById(Long id) {
        return institutionRepository.findById(id)
                .map(institutionMapper::toDto)
                .orElse(null);
    }
}
