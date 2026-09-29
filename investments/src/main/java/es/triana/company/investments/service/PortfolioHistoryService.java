package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.InstrumentHistoryDTO;
import es.triana.company.investments.model.api.PortfolioHistoryDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;

@Service
public class PortfolioHistoryService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentTypeCatalogRepository typeRepository;
    private final InstrumentHistoryService instrumentHistoryService;

    public PortfolioHistoryService(
            InvestmentRepository investmentRepository,
            InvestmentTypeCatalogRepository typeRepository,
            InstrumentHistoryService instrumentHistoryService) {
        this.investmentRepository = investmentRepository;
        this.typeRepository = typeRepository;
        this.instrumentHistoryService = instrumentHistoryService;
    }

    @Transactional(readOnly = true)
    public PortfolioHistoryDTO getHistory(Long tenantId, List<String> typeCodes, LocalDate from, LocalDate to) {
        Set<String> requestedTypes = typeCodes == null ? Set.of() : typeCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<Long, String> typeCodesById = typeRepository.findAll().stream()
                .collect(Collectors.toMap(type -> type.getId(), type -> type.getCode().toUpperCase(Locale.ROOT)));

        List<Long> instrumentIds = investmentRepository.findByTenantIdOrderByUpdatedAtDescIdDesc(tenantId).stream()
                .filter(investment -> requestedTypes.isEmpty()
                        || requestedTypes.contains(typeCodesById.get(investment.getTypeId())))
                .map(Investment::getInstrumentId)
                .distinct()
                .toList();

        List<TreeMap<LocalDate, BigDecimal>> series = instrumentIds.stream()
                .map(instrumentId -> toValueSeries(instrumentHistoryService.getHistory(tenantId, instrumentId, from, to)))
                .filter(values -> !values.isEmpty())
                .toList();

        Set<LocalDate> dates = series.stream()
                .flatMap(values -> values.keySet().stream())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        List<PortfolioHistoryDTO.Point> points = new ArrayList<>();
        for (LocalDate date : dates) {
            BigDecimal value = series.stream()
                    .map(values -> values.floorEntry(date))
                    .filter(entry -> entry != null)
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new PortfolioHistoryDTO.Point(date, value));
        }

        return new PortfolioHistoryDTO(requestedTypes.stream().sorted().toList(), points);
    }

    private TreeMap<LocalDate, BigDecimal> toValueSeries(InstrumentHistoryDTO history) {
        return history.points().stream()
                .filter(point -> point.valueEur() != null)
                .collect(Collectors.toMap(
                        InstrumentHistoryDTO.Point::date,
                        InstrumentHistoryDTO.Point::valueEur,
                        (left, right) -> right,
                        TreeMap::new));
    }
}