/*
 * Copyright (C) 2026 RBMK Simulator contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.rbmksim.jev;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin client for TypeSafe's System One HTTP API.
 * <p>
 * Documented contract (https://docs.typesafe.ai/api):
 * {@code POST https://api.typesafe.ai/v1/systemone} with
 * {@code Authorization: Bearer $TYPESAFE_API_KEY} and a JSON body of
 * {@code model}, {@code state}, and {@code questions}. A noul answer is
 * {@code { "type": "noul", "noul": <0..1> }}. A choice answer is
 * {@code { "type": "choice", "choice": <key>, "probabilities": {...}, "confidence": <0..1> }}.
 * The {@code model} field of the response is the version that actually served
 * the call. There is no Java SDK; the official clients are Python
 * ({@code typesafe-sdk}) and JavaScript ({@code @typesafe-ai/sdk}), and both
 * speak this same endpoint.
 */
public final class JevClient {

    static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private JevClient() {
    }

    /**
     * Parses the request body the client would send, so a demo can fail
     * fast if the JSON shape drifts from the documented object.
     */
    public static void checkRequestShape(Az5PlantState state) throws JevUnavailableException {
        Map<String, Object> parsed = JevJson.parseObject(
                JevQuestions.requestBody(state, JevQuestions.DEFAULT_MODEL));
        if (!(parsed.get("model") instanceof String)
                || !(parsed.get("state") instanceof Map<?, ?>)
                || !(parsed.get("questions") instanceof Map<?, ?> questions)
                || !questions.containsKey(JevQuestions.SPIKE)
                || !questions.containsKey(JevQuestions.ACTION)) {
            throw new JevUnavailableException("AZ-5 request JSON is missing model, state, or questions.");
        }
    }

    static Az5Decision evaluate(Az5PlantState state) throws JevUnavailableException {
        String apiKey = System.getenv("TYPESAFE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new JevUnavailableException(
                    "TYPESAFE_API_KEY is not set, so POST " + ENDPOINT + " was not sent.");
        }
        String model = System.getenv("TYPESAFE_MODEL");
        if (model == null || model.isBlank()) {
            model = JevQuestions.DEFAULT_MODEL;
        }
        String body = JevQuestions.requestBody(state, model);
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendWithOneRetry(request);
        if (response.statusCode() == 401) {
            throw new JevUnavailableException(
                    "Jev returned 401. The TYPESAFE_API_KEY was rejected.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new JevUnavailableException(
                    "Jev returned HTTP " + response.statusCode() + ".");
        }
        return parseDecision(response.body());
    }

    /**
     * One retry for 429 and 529, which the API reference says to back off.
     * Other failures surface immediately so a scram decision is not delayed
     * by a long retry loop. The local stand-in covers a failed call.
     */
    private static HttpResponse<String> sendWithOneRetry(HttpRequest request)
            throws JevUnavailableException {
        try {
            HttpResponse<String> response = HTTP.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 && response.statusCode() != 529) {
                return response;
            }
            Thread.sleep(retryDelayMillis(response));
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new JevUnavailableException("Interrupted while calling Jev.", ex);
        } catch (Exception ex) {
            throw new JevUnavailableException(
                    "Could not reach " + ENDPOINT + ": " + ex.getClass().getSimpleName(),
                    ex);
        }
    }

    private static long retryDelayMillis(HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("retry-after").orElse("");
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.min(seconds, 2L) * 1000L;
        } catch (NumberFormatException ex) {
            return 500L;
        }
    }

    public static Az5Decision parseDecision(String responseBody) throws JevUnavailableException {
        Map<String, Object> root = JevJson.parseObject(responseBody);
        Object modelValue = root.get("model");
        if (!(modelValue instanceof String model) || model.isBlank()) {
            throw new JevUnavailableException("Jev response had no model id.");
        }
        Object answersValue = root.get("answers");
        if (!(answersValue instanceof Map<?, ?> answers)) {
            throw new JevUnavailableException("Jev response had no answers object.");
        }
        double noul = numberField(answer(answers, JevQuestions.SPIKE), "noul");
        Map<?, ?> action = answer(answers, JevQuestions.ACTION);
        Object choiceValue = action.get("choice");
        if (!(choiceValue instanceof String choice) || choice.isBlank()) {
            throw new JevUnavailableException("Jev choice answer had no choice.");
        }
        double confidence = action.get("confidence") instanceof Number number
                ? number.doubleValue()
                : Double.NaN;
        return new Az5Decision(
                Az5Decision.SOURCE_JEV,
                model,
                choice,
                noul,
                confidence,
                "Live Jev answer from " + ENDPOINT + ".");
    }

    private static Map<?, ?> answer(Map<?, ?> answers, String name) throws JevUnavailableException {
        Object value = answers.get(name);
        if (!(value instanceof Map<?, ?> answer)) {
            throw new JevUnavailableException("Jev response missing answer '" + name + "'.");
        }
        return answer;
    }

    private static double numberField(Map<?, ?> object, String name) throws JevUnavailableException {
        Object value = object.get(name);
        if (!(value instanceof Number number)) {
            throw new JevUnavailableException("Jev answer field '" + name + "' was not a number.");
        }
        return number.doubleValue();
    }
}
