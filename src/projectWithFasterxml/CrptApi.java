package projectWithFasterxml;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int requestLimit;
    private final long timeIntervalMillis;
    private final BlockingQueue<Long> requestTimestamps;
    private final AtomicInteger requestCount;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestTimestamps = new LinkedBlockingQueue<>(requestLimit);
        this.requestCount = new AtomicInteger(0);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(document);

        synchronized (this) {
            while (requestCount.get() >= requestLimit) {
                long oldestTimestamp = requestTimestamps.peek();
                long currentTime = System.currentTimeMillis();
                if (currentTime - oldestTimestamp > timeIntervalMillis) {
                    requestTimestamps.poll();
                    requestCount.decrementAndGet();
                } else {
                    wait(timeIntervalMillis - (currentTime - oldestTimestamp));
                }
            }
            requestTimestamps.offer(System.currentTimeMillis());
            requestCount.incrementAndGet();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create document: " + response.body());
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
}
