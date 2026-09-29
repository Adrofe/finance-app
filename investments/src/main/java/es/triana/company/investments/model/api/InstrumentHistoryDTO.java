package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstrumentHistoryDTO(
        Long instrumentId,
        String symbol,
        String name,
        String currency,
        List<Point> points) {

    public record Point(
            LocalDate date,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal investedEur,
            BigDecimal valueEur) {
    }
}