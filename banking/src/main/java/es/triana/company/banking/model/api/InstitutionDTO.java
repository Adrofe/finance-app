package es.triana.company.banking.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionDTO {

    private Long id;
    private String name;
    private String country;
    private String website;
}
