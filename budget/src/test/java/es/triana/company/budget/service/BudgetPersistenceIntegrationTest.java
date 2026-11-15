package es.triana.company.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.json.JsonMapper;

import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.BudgetPeriod;
import es.triana.company.budget.model.api.BankingCategoryDTO;
import es.triana.company.budget.model.api.BankingTransactionDTO;
import es.triana.company.budget.model.db.BudgetPlan;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.repository.BudgetPlanRepository;
import es.triana.company.budget.repository.BudgetSnapshotRepository;
import es.triana.company.budget.service.client.BankingApiClient;
import jakarta.persistence.EntityManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.schemas=budget" })
@Transactional
class BudgetPersistenceIntegrationTest {
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:18");

    @BeforeAll
    static void startDatabase() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for PostgreSQL integration tests");
        DATABASE.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @AfterAll
    static void stopDatabase() { if (DATABASE.isRunning()) DATABASE.stop(); }

    @Autowired private BudgetService service;
    @Autowired private BudgetPlanRepository plans;
    @Autowired private BudgetSnapshotRepository snapshots;
    @Autowired private EntityManager entityManager;
    @MockitoBean private BankingApiClient banking;
    @MockitoBean private JwtDecoder jwtDecoder;
    @LocalServerPort private int port;

    @Test
    void persistsMonthlyBreakdownAndKeepsPrimaryPlansIsolatedByTenant() {
        BudgetPlan first = createPlan(1L, "First");
        BudgetPlan second = createPlan(1L, "Second");
        BudgetPlan otherTenant = createPlan(2L, "Other tenant");
        service.setPrimaryPlan(1L, first.getId());
        service.setPrimaryPlan(2L, otherTenant.getId());
        service.setPrimaryPlan(1L, second.getId());
        entityManager.flush();
        entityManager.clear();
        assertFalse(plans.findById(first.getId()).orElseThrow().getPrimary());
        assertTrue(plans.findById(second.getId()).orElseThrow().getPrimary());
        assertTrue(plans.findById(otherTenant.getId()).orElseThrow().getPrimary());

        LocalDate start = LocalDate.of(2025, 12, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        when(banking.getCategories("token")).thenReturn(List.of(BankingCategoryDTO.builder().id(10L).name("Food").code("FOOD").build()));
        when(banking.getTransactions("token", start, end)).thenReturn(List.of(
                BankingTransactionDTO.builder().categoryId(10L).bookingDate(start.atStartOfDay()).amount(-120.0).currency("EUR").build(),
                BankingTransactionDTO.builder().categoryId(10L).bookingDate(end.atStartOfDay()).amount(-50.0).currency("EUR").build()));
        service.refreshSnapshot(1L, "token", second.getId(), start, end);
        entityManager.flush();
        entityManager.clear();

        var stored = service.getLatestSnapshot(1L, second.getId());
        assertEquals(2, stored.getMonths().size());
        assertEquals(0, stored.getTotalBudget().compareTo(new BigDecimal("200.00")));
        assertFalse(stored.getMonths().get(0).getCompliant());
        assertTrue(stored.getMonths().get(1).getCompliant());
        assertEquals(1, stored.getLines().size());
        String json = new JsonMapper().writeValueAsString(stored);
        assertTrue(json.contains("\"periodStart\":\"2025-12-01\""));
        assertTrue(json.contains("\"monthStart\":\"2025-12-01\""));
        assertTrue(new JsonMapper().writeValueAsString(service.getPlan(1L, second.getId())).contains("\"primary\":true"));

        service.refreshSnapshot(1L, "token", second.getId(), start, end);
        entityManager.flush();
        entityManager.clear();
        assertEquals(2, service.getLatestSnapshot(1L, second.getId()).getMonths().size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRefreshesOfTheSamePeriodProduceOneSnapshot() throws Exception {
        BudgetPlan plan = createPlan(20L, "Concurrent plan");
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        when(banking.getCategories("token")).thenReturn(List.of(BankingCategoryDTO.builder().id(10L).name("Food").code("FOOD").build()));
        CountDownLatch firstFetch = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(banking.getTransactions("token", start, end)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstFetch.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for the second refresh");
            }
            return List.of();
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> service.refreshSnapshot(20L, "token", plan.getId(), start, end), executor);
            assertTrue(firstFetch.await(10, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            var second = CompletableFuture.supplyAsync(() -> {
                secondStarted.countDown();
                return service.refreshSnapshot(20L, "token", plan.getId(), start, end);
            }, executor);
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS).getId(), second.get(15, TimeUnit.SECONDS).getId());
            assertEquals(1, snapshots.findAllByTenantIdAndPlanIdOrderByPeriodStartDescGeneratedAtDesc(20L, plan.getId()).size());
        } finally {
            release.countDown();
        }
    }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void authenticatedApiReturnsPrimaryPlanAndMonthlyData() throws Exception {
        Jwt jwt = Jwt.withTokenValue("tenant-token").header("alg", "none").subject("test")
            .claim("tenant_id", 30L).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode("tenant-token")).thenReturn(jwt);
        when(banking.getCategories("tenant-token")).thenReturn(List.of(BankingCategoryDTO.builder().id(10L).name("Food").code("FOOD").build()));
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);
        when(banking.getTransactions("tenant-token", start, end)).thenReturn(List.of(
            BankingTransactionDTO.builder().categoryId(10L).bookingDate(start.atStartOfDay()).amount(-120.0).currency("EUR").build()));
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port + "/v1/api/budget/plans";
        String body = """
            {"name":"HTTP plan","currency":"EUR","period":"MONTHLY","lines":[{"categoryId":10,"categoryCode":"FOOD","categoryName":"Food","budgetAmount":100,"lineType":"EXPENSE"}]}
            """;
        HttpRequest create = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer tenant-token")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> created = client.send(create, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode(), created.body());
        long id = new JsonMapper().readTree(created.body()).get("data").get("id").asLong();
        HttpRequest primary = HttpRequest.newBuilder(URI.create(base + "/" + id + "/primary")).header("Authorization", "Bearer tenant-token").PUT(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> primaryResponse = client.send(primary, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, primaryResponse.statusCode(), primaryResponse.body());
        assertTrue(primaryResponse.body().contains("\"primary\":true"));
        HttpRequest refresh = HttpRequest.newBuilder(URI.create(base + "/" + id + "/snapshots/refresh?startDate=2026-01-01&endDate=2026-02-28"))
            .header("Authorization", "Bearer tenant-token").POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> refreshed = client.send(refresh, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        var data = new JsonMapper().readTree(refreshed.body()).get("data");
        assertEquals("2026-01-01", data.get("periodStart").asString());
        assertEquals(2, data.get("months").size());
        assertEquals(200.0, data.get("totalBudget").asDouble());
        assertFalse(data.get("months").get(0).get("compliant").asBoolean());
        assertTrue(data.get("months").get(1).get("compliant").asBoolean());

        when(banking.getTransactions("tenant-token", start, end)).thenReturn(List.of(
            BankingTransactionDTO.builder().categoryId(10L).bookingDate(start.atStartOfDay()).amount(-10.0).currency("USD").build()));
        HttpResponse<String> mixedCurrency = client.send(refresh, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, mixedCurrency.statusCode(), mixedCurrency.body());
        HttpRequest latest = HttpRequest.newBuilder(URI.create(base + "/" + id + "/snapshots/latest")).header("Authorization", "Bearer tenant-token").GET().build();
        var latestData = new JsonMapper().readTree(client.send(latest, HttpResponse.BodyHandlers.ofString()).body()).get("data");
        assertEquals(120.0, latestData.get("totalSpent").asDouble());

        Jwt otherJwt = Jwt.withTokenValue("other-token").header("alg", "none").subject("other")
            .claim("tenant_id", 31L).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode("other-token")).thenReturn(otherJwt);
        HttpRequest otherPrimary = HttpRequest.newBuilder(URI.create(base + "/" + id + "/primary")).header("Authorization", "Bearer other-token").PUT(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(404, client.send(otherPrimary, HttpResponse.BodyHandlers.ofString()).statusCode());

        String customBody = """
            {"name":"Custom plan","currency":"EUR","period":"CUSTOM","startDate":"2026-01-15","endDate":"2026-03-02","lines":[{"categoryId":10,"budgetAmount":100,"lineType":"EXPENSE"}]}
            """;
        HttpRequest custom = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer tenant-token")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(customBody)).build();
        HttpResponse<String> customResponse = client.send(custom, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, customResponse.statusCode(), customResponse.body());
        var customData = new JsonMapper().readTree(customResponse.body()).get("data");
        assertEquals("CUSTOM", customData.get("period").asString());
        assertEquals("2026-01-15", customData.get("startDate").asString());
        assertEquals("2026-03-02", customData.get("endDate").asString());
        }

    private BudgetPlan createPlan(Long tenantId, String name) {
        LocalDateTime now = LocalDateTime.now();
        BudgetPlan plan = BudgetPlan.builder().tenantId(tenantId).name(name).currency("EUR").active(true)
                .period(BudgetPeriod.MONTHLY).createdAt(now).updatedAt(now).build();
        plan.getLines().add(BudgetPlanLine.builder().plan(plan).categoryId(10L).categoryCode("FOOD").categoryName("Food")
                .budgetAmount(new BigDecimal("100.00")).lineType(BudgetLineType.EXPENSE).createdAt(now).updatedAt(now).build());
        return plans.saveAndFlush(plan);
    }
}