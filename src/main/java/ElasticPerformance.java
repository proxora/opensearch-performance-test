import com.google.common.io.Resources;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElasticPerformance {

    private static List<String> firstNames;
    private static List<String> lastNames;
    private static final String simpleDocumentTemplateStart = "{ \"Name\": \"";
    private static final String simpleDocumentTemplateEnd = "\" }";

    public static void main(String[] args) throws Exception {
        System.out.println("Elasticsearch - performance test");
        firstNames = Resources.readLines(Resources.getResource("firstNames.txt"), StandardCharsets.UTF_8);
        lastNames = Resources.readLines(Resources.getResource("lastNames.txt"), StandardCharsets.UTF_8);
        Random generationSeed = new Random(1);
        try (RestHighLevelClient client = createRestClient()) {
            if (!checkIfIndexExists(client, "simple")) {
                System.out.println("Creating index \"simple\"...");
                createIndex(client, "simple", "settings.yaml", "simpleMapping.yaml");

                System.out.println("Adding documents (1.000.000)...");
                printProgress(0, 40, false);
                for (int i = 0; i < 1000; i++) {
                    addDocuments(client, "simple", Stream.generate(() -> createSimpleDocument(generationSeed)).limit(1000).collect(Collectors.toList()));
                    printProgress((float) i / 1000, 40, true);
                }
                System.out.print("\n");

                System.out.println("Refreshing index \"simple\"...");
                refreshIndex(client, "simple");
            }
            Random testingSeed = new Random(2);
            SearchResult onetwogram = new SearchResult();
            SearchResult basic = new SearchResult();
            System.out.println("Executing search queries...");
            printProgress(0, 40, false);
            for (int i = 0; i < 1000; i++) {
                onetwogram.add(searchForQuery(client, "simple", simpleQuery("Name.onetwogram", testingSeed)));
                basic.add(searchForQuery(client, "simple", simpleQuery("Name.basic", testingSeed)));
                printProgress((float) i / 1000, 40, true);
            }
            System.out.print("\n");

            System.out.println(">>> onetwogram:\n" + onetwogram.log());
            System.out.println(">>> basic:\n" + basic.log());
        }
    }

    private static void printProgress(float progress, int length, boolean replace) {
        if (replace) {
            System.out.print("\r");
        }
        final int progressElements = Math.round(length * progress);
        System.out.print("|"
                + String.join("", Collections.nCopies(progressElements, "#"))
                + String.join("", Collections.nCopies(length - progressElements, "-"))
                + "|");
    }

    private static boolean checkIfIndexExists(RestHighLevelClient client, String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    private static void createIndex(RestHighLevelClient client, String indexName, String settingsFile, String mappingName)
            throws IOException {
        CreateIndexRequest nestedCreateIndexRequest = new CreateIndexRequest(indexName);
        nestedCreateIndexRequest.settings(Resources.toString(Resources.getResource(settingsFile), StandardCharsets.UTF_8), XContentType.YAML);
        nestedCreateIndexRequest.mapping(Resources.toString(Resources.getResource(mappingName), StandardCharsets.UTF_8), XContentType.YAML);
        client.indices().create(nestedCreateIndexRequest, RequestOptions.DEFAULT);
    }

    private static void addDocuments(RestHighLevelClient client, String indexName, List<String> documents)
            throws IOException {
        BulkRequest request = new BulkRequest(indexName);
        documents.stream().map(document -> addDocument(client, indexName, document)).forEach(request::add);
        client.bulk(request, RequestOptions.DEFAULT);
    }

    private static IndexRequest addDocument(RestHighLevelClient client, String index, String document) {
        IndexRequest request = new IndexRequest(index);
        request.source(document, XContentType.JSON);
        return request;
    }

    private static void refreshIndex(RestHighLevelClient client, String indexName) throws IOException {
        RefreshRequest request = new RefreshRequest(indexName);
        client.indices().refresh(request, RequestOptions.DEFAULT);
    }

    private static SearchResult searchForQuery(RestHighLevelClient client, String indexName, QueryBuilder query)
            throws IOException {
        SearchRequest request = new SearchRequest(indexName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(query);
        searchSourceBuilder.size(100);
        request.source(searchSourceBuilder);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        return new SearchResult(
                response.getTook().millis(),
                response.getHits().getTotalHits().value);
    }

    private static QueryBuilder simpleQuery(String fieldName, Random seed) {
        return QueryBuilders.matchQuery(fieldName, createRandomName(seed)).minimumShouldMatch("75%");
    }

    private static String createSimpleDocument(Random seed) {
        String randomName = createRandomName(seed);
        return simpleDocumentTemplateStart + randomName + simpleDocumentTemplateEnd;
    }

    private static String createRandomName(Random seed) {
        String firstName = firstNames.get(seed.nextInt(firstNames.size()));
        String lastName = lastNames.get(seed.nextInt(lastNames.size()));
        return lastName + ", " + firstName;
    }

    private static RestHighLevelClient createRestClient() {
        return new RestHighLevelClient(RestClient
                .builder(new HttpHost("localhost", 9200)));
    }

    private static class SearchResult {
        List<Long> took = new ArrayList<>();
        List<Long> resultSize = new ArrayList<>();

        SearchResult() {
        }

        SearchResult(long took, long resultSize) {
            this.took.add(took);
            this.resultSize.add(resultSize);
        }

        void add(SearchResult result) {
            this.took.addAll(result.took);
            this.resultSize.addAll(result.resultSize);
        }

        String log() {
            return "Took:\n"
                    + "  Total:          " + took.stream().mapToLong(l -> l).sum() + " ms\n"
                    + "  Max:            " + took.stream().mapToLong(l -> l).max().orElse(-1L) + " ms\n"
                    + "  Min:            " + took.stream().mapToLong(l -> l).min().orElse(-1L) + " ms\n"
                    + "  Average:        " + took.stream().mapToLong(l -> l).average().orElse(-1L) + " ms\n"
                    + "  Median:         " + took.stream().sorted().skip((long) (took.size() * 0.5)).findFirst().orElse(-1L) + " ms\n"
                    + "  90% Percentile: " + took.stream().sorted().skip((long) (took.size() * 0.9)).findFirst().orElse(-1L) + " ms\n"
                    + "  95% Percentile: " + took.stream().sorted().skip((long) (took.size() * 0.95)).findFirst().orElse(-1L) + " ms\n"
                    + "  99% Percentile: " + took.stream().sorted().skip((long) (took.size() * 0.99)).findFirst().orElse(-1L) + " ms\n"
                    + "Results:\n"
                    + "  Total:          " + resultSize.stream().mapToLong(l -> l).sum() + "\n"
                    + "  Max:            " + resultSize.stream().mapToLong(l -> l).max().orElse(-1L) + "\n"
                    + "  Min:            " + resultSize.stream().mapToLong(l -> l).min().orElse(-1L)
                    ;
        }
    }

}
