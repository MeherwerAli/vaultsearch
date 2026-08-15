package dev.meherwerali.vaultsearch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meherwerali.vaultsearch.search.BacklinkResult;
import dev.meherwerali.vaultsearch.search.NoteSearchDocument;
import dev.meherwerali.vaultsearch.search.SearchResult;
import dev.meherwerali.vaultsearch.search.VaultSearchService;
import dev.meherwerali.vaultsearch.sync.IndexTaskWorker;
import dev.meherwerali.vaultsearch.sync.ReconciliationResult;
import dev.meherwerali.vaultsearch.sync.VaultReconciler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VaultSearchIntegrationTest {

    private static final Path VAULT_ROOT = createVaultRoot();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("vaultsearch")
            .withUsername("vaultsearch")
            .withPassword("vaultsearch");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.1"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
        registry.add("vaultsearch.vault-root", VAULT_ROOT::toString);
        registry.add("vaultsearch.api-key", () -> "integration-key");
        registry.add("vaultsearch.watcher-enabled", () -> "false");
        registry.add("vaultsearch.startup-reconcile-enabled", () -> "false");
        registry.add("vaultsearch.scheduled-index-worker-enabled", () -> "false");
        registry.add("vaultsearch.reconcile-interval", () -> "1h");
        registry.add("vaultsearch.index-worker-delay", () -> "1h");
    }

    @BeforeAll
    static void createFixture() throws IOException {
        Files.createDirectories(VAULT_ROOT.resolve("Runbooks"));
        Files.writeString(VAULT_ROOT.resolve("Architecture.md"), """
                ---
                tags: [architecture]
                ---
                # Architecture
                Reliability depends on [[Runbooks/Health]].
                """);
        Files.writeString(VAULT_ROOT.resolve("Runbooks/Health.md"), "# Health\n\nDependency triage.");
    }

    @Autowired
    VaultReconciler reconciler;

    @Autowired
    IndexTaskWorker worker;

    @Autowired
    VaultSearchService searchService;

    @Autowired
    ElasticsearchOperations operations;

    @Autowired
    TestRestTemplate http;

    @Test
    void indexesSearchesAndReturnsBacklinksThroughTheDurableProjection() throws Exception {
        ReconciliationResult result = reconciler.reconcile();
        assertThat(result.parsed()).isEqualTo(2);
        assertThat(result.queuedUpserts()).isEqualTo(2);
        assertThat(worker.processDueTasks()).isEqualTo(2);
        operations.indexOps(NoteSearchDocument.class).refresh();

        SearchResult search = searchService.search("dependency", 10);
        assertThat(search.hits()).extracting(SearchResult.Hit::title).contains("Health");
        String healthId = search.hits().stream().filter(hit -> hit.title().equals("Health")).findFirst().orElseThrow().noteId();

        BacklinkResult backlinks = searchService.backlinks(healthId, 10);
        assertThat(backlinks.backlinks()).extracting(BacklinkResult.Source::title).containsExactly("Architecture");

        assertThat(http.getForEntity("/api/v1/search?q=dependency", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        HttpHeaders authorization = new HttpHeaders();
        authorization.setBearerAuth("integration-key");
        var authorized = http.exchange("/api/v1/search?q=dependency&limit=10", HttpMethod.GET,
                new HttpEntity<>(authorization), String.class);
        assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authorized.getBody()).contains("Runbooks/Health.md").doesNotContain(VAULT_ROOT.toString());
        var invalid = http.exchange("/api/v1/search?q=dependency&limit=51", HttpMethod.GET,
                new HttpEntity<>(authorization), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        ReconciliationResult duplicate = reconciler.reconcile();
        assertThat(duplicate.queuedUpserts()).isZero();
        assertThat(duplicate.queuedDeletes()).isZero();
        assertThat(worker.processDueTasks()).isZero();

        Path health = VAULT_ROOT.resolve("Runbooks/Health.md");
        Files.writeString(health, "# Health\n\nDegraded dependency triage.");
        ReconciliationResult modification = reconciler.reconcile();
        assertThat(modification.queuedUpserts()).isEqualTo(1);
        assertThat(worker.processDueTasks()).isEqualTo(1);
        operations.indexOps(NoteSearchDocument.class).refresh();
        assertThat(searchService.search("degraded", 10).hits()).extracting(SearchResult.Hit::relativePath)
                .containsExactly("Runbooks/Health.md");

        Path renamed = VAULT_ROOT.resolve("Runbooks/Service Health.md");
        Files.move(health, renamed);
        ReconciliationResult rename = reconciler.reconcile();
        assertThat(rename.queuedUpserts()).isEqualTo(2);
        assertThat(rename.queuedDeletes()).isEqualTo(1);
        assertThat(worker.processDueTasks()).isEqualTo(3);
        operations.indexOps(NoteSearchDocument.class).refresh();
        SearchResult renamedSearch = searchService.search("degraded", 10);
        assertThat(renamedSearch.hits()).extracting(SearchResult.Hit::relativePath)
                .containsExactly("Runbooks/Service Health.md");

        Files.delete(VAULT_ROOT.resolve("Architecture.md"));
        ReconciliationResult deletion = reconciler.reconcile();
        assertThat(deletion.queuedDeletes()).isEqualTo(1);
        assertThat(worker.processDueTasks()).isEqualTo(1);
        operations.indexOps(NoteSearchDocument.class).refresh();
        String renamedId = renamedSearch.hits().get(0).noteId();
        assertThat(searchService.backlinks(renamedId, 10).backlinks()).isEmpty();
    }

    private static Path createVaultRoot() {
        try {
            return Files.createTempDirectory("vaultsearch-integration-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
