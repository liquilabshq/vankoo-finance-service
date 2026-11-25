package com.liquilabs.vankoo.finance.infrastructure.providers.stripe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stub HTTP de la API de Stripe para pruebas de integración del adaptador.
 *
 * <p>Levanta un servidor local (puerto efímero) con el {@link HttpServer} del
 * JDK, de modo que el SDK real de Stripe serializa, envía y deserializa como en
 * producción, pero sin salir a internet ni usar credenciales reales. Las
 * respuestas se encolan en orden y cada solicitud recibida queda registrada
 * para poder verificar lo que el adaptador envió.
 *
 * <p>Se usa el servidor del JDK y no WireMock para no tocar {@code pom.xml},
 * que es un punto de coordinación del equipo.
 *
 * @author LiquiLabs
 */
final class StripeApiStub implements AutoCloseable {

    /** Solicitud recibida por el stub, tal como la envió el SDK. */
    record RecordedRequest(String method, String path, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }

        /** Cuerpo form-urlencoded decodificado, para leer claves como {@code metadata[deposit_id]}. */
        String decodedBody() {
            return URLDecoder.decode(body, StandardCharsets.UTF_8);
        }
    }

    private record StubResponse(int status, String json) {
    }

    private final HttpServer server;
    private final Deque<StubResponse> responses = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    StripeApiStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    StripeApiStub willRespond(int status, String json) {
        synchronized (responses) {
            responses.add(new StubResponse(status, json));
        }
        return this;
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(), path, Map.copyOf(exchange.getRequestHeaders()), body));

        StubResponse response;
        synchronized (responses) {
            response = responses.poll();
        }
        if (response == null) {
            response = new StubResponse(500, error("api_error", "No stubbed response for " + path));
        }

        byte[] bytes = response.json().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Request-Id", "req_stub_" + requests.size());
        exchange.sendResponseHeaders(response.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** Cuerpo de error con el formato que devuelve Stripe. */
    static String error(String type, String message) {
        return """
                {"error": {"type": "%s", "message": "%s"}}
                """.formatted(type, message);
    }

    /** Checkout Session mínima con los campos que el adaptador lee. */
    static String session(String id, String status, String paymentStatus) {
        return """
                {
                  "id": "%s",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/pay/%s",
                  "status": "%s",
                  "payment_status": "%s",
                  "mode": "payment",
                  "livemode": false
                }
                """.formatted(id, id, status, paymentStatus);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
