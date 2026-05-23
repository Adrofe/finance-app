package es.triana.company.investments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import es.triana.company.investments.service.OperationService;

/**
 * Runs on startup to recalculate all investment positions using the average cost method.
 * Ensures that investedAmount reflects the cost basis of the remaining holding,
 * not the total capital ever deployed.
 */
@Component
public class PositionRecalculationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PositionRecalculationRunner.class);

    private final OperationService operationService;

    public PositionRecalculationRunner(OperationService operationService) {
        this.operationService = operationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOG.info("Running startup investment position recalculation...");
        int count = operationService.recalculateAllPositions();
        LOG.info("Startup recalculation complete: {} positions updated", count);
    }
}
