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
    private final int requestLimit;
    private final long timeIntervalMillis;
    private final BlockingQueue<Long> requestTimestamps;
    private final AtomicInteger requestCount;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestTimestamps = new LinkedBlockingQueue<>(requestLimit);
        this.requestCount = new AtomicInteger(0);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
//        Create "document" to json-stroke
        String requestBody = toJson(document);
//        Sync and check count requests
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

//       Create and send request
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

    //    Create main to json
    private String toJson(Document document) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"description\":").append(toJson(document.description)).append(",");
        json.append("\"doc_id\":\"").append(document.doc_id).append("\",");
        json.append("\"doc_status\":\"").append(document.doc_status).append("\",");
        json.append("\"doc_type\":\"").append(document.doc_type).append("\",");
        json.append("\"importRequest\":").append(document.importRequest).append(",");
        json.append("\"owner_inn\":\"").append(document.owner_inn).append("\",");
        json.append("\"participant_inn\":\"").append(document.participant_inn).append("\",");
        json.append("\"producer_inn\":\"").append(document.producer_inn).append("\",");
        json.append("\"production_date\":\"").append(document.production_date).append("\",");
        json.append("\"production_type\":\"").append(document.production_type).append("\",");
        json.append("\"products\":[").append(toJson(document.products)).append("],");
        json.append("\"reg_date\":\"").append(document.reg_date).append("\",");
        json.append("\"reg_number\":\"").append(document.reg_number).append("\"");
        json.append("}");
        return json.toString();
    }


    private String toJson(Document.Description description) {
        return "{\"participantInn\":\"" + description.participantInn + "\"}";
    }


    private String toJson(Document.Product[] products) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < products.length; i++) {
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"certificate_document\":\"").append(products[i].certificate_document).append("\",");
            json.append("\"certificate_document_date\":\"").append(products[i].certificate_document_date).append("\",");
            json.append("\"certificate_document_number\":\"").append(products[i].certificate_document_number).append("\",");
            json.append("\"owner_inn\":\"").append(products[i].owner_inn).append("\",");
            json.append("\"producer_inn\":\"").append(products[i].producer_inn).append("\",");
            json.append("\"production_date\":\"").append(products[i].production_date).append("\",");
            json.append("\"tnved_code\":\"").append(products[i].tnved_code).append("\",");
            json.append("\"uit_code\":\"").append(products[i].uit_code).append("\",");
            json.append("\"uitu_code\":\"").append(products[i].uitu_code).append("\"");
            json.append("}");
        }
        return json.toString();
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
