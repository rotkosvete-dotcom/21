package ru.talentshow;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Любой OpenAI-совместимый эндпоинт (OpenRouter и т.п.). Если выключен или ошибка — вернёт null, мод возьмёт шаблон. */
public class AiClient {
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    static JsonObject msg(String role, String text) {
        JsonObject o = new JsonObject(); o.addProperty("role", role); o.addProperty("content", text); return o;
    }

    public static CompletableFuture<String> ask(String system, String prompt) {
        Data.AiCfg c = Data.AI;
        if (c == null || !c.enabled || c.key.isBlank()) return CompletableFuture.completedFuture(null);
        JsonObject body = new JsonObject();
        body.addProperty("model", c.model);
        body.addProperty("max_tokens", 200);
        JsonArray m = new JsonArray(); m.add(msg("system", system)); m.add(msg("user", prompt));
        body.add("messages", m);
        HttpRequest req = HttpRequest.newBuilder(URI.create(c.url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + c.key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
            try {
                return JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("choices").get(0)
                        .getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
            } catch (Exception e) { return (String) null; }
        }).exceptionally(e -> null);
    }
}
