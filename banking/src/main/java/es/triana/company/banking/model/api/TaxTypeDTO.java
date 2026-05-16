package es.triana.company.banking.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTypeDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
}
