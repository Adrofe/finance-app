package es.triana.company.investments.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentPlatformDTO {

    private Long id;

    @NotBlank(message = "code is required")
    @Size(max = 32, message = "code max length is 32")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 120, message = "name max length is 120")
    private String name;
}
