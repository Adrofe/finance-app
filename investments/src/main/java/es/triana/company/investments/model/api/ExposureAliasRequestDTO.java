package es.triana.company.investments.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExposureAliasRequestDTO {

    @NotBlank
    private String sourceName;

    @NotNull
    private Long targetId;
}