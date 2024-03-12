package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    public static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private static final String CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    // timeUnit – указывает промежуток времени – секунда, минута и пр.
    // requestLimit – положительное значение, которое определяет максимальное
    // количество запросов в этом промежутке времени.
    public CrptApi(TimeUnit timeUnit, int requestLimit){

    }

    private static class Limiter {
        private final TimeUnit timeUnit;
        private final int requestLimit;
        private final Semaphore semaphore;
        private final ScheduledExecutorService scheduler;

        private Limiter(TimeUnit timeUnit, int requestLimit) {
            this.timeUnit = timeUnit;
            this.requestLimit = requestLimit;
            this.semaphore = new Semaphore(requestLimit);

            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                    this.semaphore::release,
                    0,
                    timeUnit.toMillis(1),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * В данный метод передаём объект для сериализации
     * и, с помощью вызова метода из класса Converter,
     * сериализуем объект в JSON
     */
    private String convert(Object body, Converter converter) {
        return converter.convert(body);
    }

    /**
     * Метод для создания и отправки запроса
     */
    private String requestPost (String url, String bodyString, ContentType type)
            throws InterruptedException, IOException {

        Limiter limiter = new Limiter(TimeUnit.SECONDS, 5);
        limiter.semaphore.acquire();

        Content postResult = null;
        try {
            postResult = Request.Post(url)
                    .bodyString(bodyString, type)
                    .execute().returnContent();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return postResult != null ? postResult.asString() : "";
    }

    /**
     * Метод создания документа
     * для ввода в оборот товара, произведенного в РФ
     */
    public String createDocument(Document doc, String signature) throws IOException, InterruptedException {

        Converter converterJson = new JsonConverter();
        String docJson = convert(doc, converterJson);
        Body body = new Body(docJson, Type.LP_INTRODUCE_GOODS, signature);
        String bodyJson = convert(body, converterJson);

        return requestPost(CREATE_DOCUMENT_URL, bodyJson, ContentType.APPLICATION_JSON);
    }

    interface Converter {
        String convert(Object body);
    }

    /** Класс, включающий метод
     * для сериализации объектов
     * в JSON
     */
    static class JsonConverter implements Converter {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String convert(Object body) {
            String json = null;
            try {
                json = mapper.writeValueAsString(body);
                return json;
            } catch (JsonProcessingException e) {
                logger.error(e.getMessage());
            }
            return json != null ? json : "";
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {

        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date production_date;
        private String production_type;
        private List<Products> products;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date reg_date;
        private String reg_number;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {

        private String participantInn;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Products {

        String certificate_document;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        Date certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        Date production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    static
    class Body {

        private String product_document;
        private Type type;
        private String signature;

    }

    public enum Type {
        LP_INTRODUCE_GOODS
    }

    public static void main(String[] args) throws Exception {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        String result = crptApi.createDocument(new Document(), "signature");
        System.out.println(result);
    }
}

