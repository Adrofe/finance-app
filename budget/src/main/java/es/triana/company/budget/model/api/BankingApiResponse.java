package es.triana.company.budget.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankingApiResponse<T> {
    private int status;
    private String message;
    private T data;
}
