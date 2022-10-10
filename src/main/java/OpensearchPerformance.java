import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpensearchPerformance {

    private static List<String> firstNames;
    private static List<String> lastNames;
    private static final String simpleDocumentTemplateStart = "{ \"Name\": \"";
    private static final String simpleDocumentTemplateEnd = "\" }";

    public static void main(String[] args) throws Exception {
        System.out.println("OpenSearch - performance test ...");
        firstNames = Resources.readLines(Resources.getResource("firstNames.txt"), Charsets.UTF_8);
        lastNames = Resources.readLines(Resources.getResource("lastNames.txt"), Charsets.UTF_8);
        Random generationSeed = new Random(1);
        try (RestHighLevelClient client = createRestClient()) {
            if (!checkIfIndexExists(client, "simple")) {
                System.out.println("Creating index \"simple\"...");
                createIndex(client, "simple", "settings.yaml", "simpleMapping.yaml");

                System.out.println("Adding documents (1.000.000)...");
                for (int i = 0; i < 1000; i++) {
                    addDocuments(client, "simple", Stream.generate(() -> createSimpleDocument(generationSeed)).limit(1000).collect(Collectors.toList()));
                }

                System.out.println("Refreshing index \"simple\"...");
                refreshIndex(client, "simple");
            }
            Random testingSeed = new Random(2);
            SearchResult onetwogram = new SearchResult();
            SearchResult basic = new SearchResult();
            System.out.println("Executing search queries...");
            for (int i = 0; i < 1000; i++) {
                onetwogram.add(searchForQuery(client, "simple", simpleQuery("Name.onetwogram", testingSeed)));
                basic.add(searchForQuery(client, "simple", simpleQuery("Name.basic", testingSeed)));
            }
            System.out.println(" >> onetwogram: " + onetwogram.log());
            System.out.println(" >> basic:      " + basic.log());
        }
    }

    private static boolean checkIfIndexExists(RestHighLevelClient client, String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    private static void createIndex(RestHighLevelClient client, String indexName, String settingsFile, String mappingName)
            throws IOException {
        CreateIndexRequest nestedCreateIndexRequest = new CreateIndexRequest(indexName);
        nestedCreateIndexRequest.settings(Resources.toString(Resources.getResource(settingsFile), Charsets.UTF_8), XContentType.YAML);
        nestedCreateIndexRequest.mapping(Resources.toString(Resources.getResource(mappingName), Charsets.UTF_8), XContentType.YAML);
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
        SearchResult result = new SearchResult();
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        result.took = response.getTook().millis();
        result.resultSize = response.getHits().getTotalHits().value;
        return result;
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
        final CredentialsProvider credentialsProvider =
                new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials("user", "test-user-password"));
        RestClientBuilder builder = RestClient.builder(
                        new HttpHost("localhost", 9200))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider));
        return new RestHighLevelClient(builder);
    }

    private static class SearchResult {
        long took = 0;
        long resultSize = 0;

        void add(SearchResult result) {
            took += result.took;
            resultSize += result.resultSize;
        }

        String log() {
            return "Took: " + took + " ms, # Results: " + resultSize;
        }
    }

}
