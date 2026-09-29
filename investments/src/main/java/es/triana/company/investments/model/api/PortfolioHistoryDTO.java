package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioHistoryDTO(
        List<String> appliedTypeCodes,
        List<Point> points) {

    public record Point(LocalDate date, BigDecimal valueEur) {
    }
}