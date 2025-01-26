package com.vs.starnet.star.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.model.Component;
import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.model.Sol;
import lombok.Getter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpHandlerTest {
    static final Logger LOGGER = LogManager.getRootLogger();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Getter
    public enum HttpMethodType {
        POST, GET, DELETE, PATCH;
    }

    /**
     * Builds the component payload as a JSON string using the Jackson library.
     * This method serializes a {@link Component} object into a JSON string for use in HTTP requests.
     *
     * @param component The component data to include in the payload.
     * @return The JSON string representing the component.
     * @throws IllegalArgumentException If any required fields in the component are missing or invalid.
     */
    public static String buildComponentPayload(Component component) throws Exception {
        if (component == null || component.getSolStarUuid() == null || component.getSolComUuid() == null ||
                component.getComUuid() == null || component.getComIp() == null || component.getComPort() == 0 ||
                component.getStatus() == null) {
            throw new IllegalArgumentException("Component data is incomplete.");
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(component);
    }

    /**
     * Builds the message payload as a JSON string using the Jackson library.
     * This method serializes a {@link Message} object into a JSON string for use in HTTP requests.
     *
     * @param message The message data to include in the payload.
     * @return The JSON string representing the message.
     * @throws Exception If the message object cannot be serialized to JSON.
     */
    public static String buildMessagePayload(Message message) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(message);
    }

    public static String buildSolPayload(Sol sol) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(sol);
    }

    /**
     * Sends an HTTP request with the specified method, payload, and content type.
     * <p>
     * This method uses the Java 11+ {@link HttpClient} to perform HTTP communication, supporting various HTTP methods
     * (e.g., GET, POST, DELETE, PATCH). It constructs the request dynamically based on the provided parameters,
     * sends the request to the specified URL, and returns the response as a {@link HttpResponse}.
     * <p>
     * In case of errors, such as network issues or request interruptions, this method logs the error and throws a
     * {@link RuntimeException} to the caller.
     *
     * @param urlString   The full URL to which the request will be sent (e.g., "http://example.com/vs/v1/system").
     * @param jsonPayload The JSON payload to include in the request body, or {@code null} if no body is required
     *                    (e.g., for GET requests).
     * @param methodType  The HTTP method to use for the request (e.g., POST, GET, DELETE, PATCH).
     *                    Must be one of {@link HttpHandler.HttpMethodType}.
     * @param contentType The value of the "Content-Type" header (e.g., "application/json").
     *                    Required for methods with a payload (e.g., POST, PATCH).
     * @return A {@link HttpResponse} containing the status code, headers, and body returned by the server.
     * @throws RuntimeException If the request fails due to an {@link IOException} (e.g., unreachable server) or
     *                          an {@link InterruptedException} (e.g., request was interrupted).
     *                          The exception includes detailed logs for debugging purposes.
     */
    private static HttpResponse<String> sendRequest(String urlString, String jsonPayload, HttpHandler.HttpMethodType methodType, String contentType) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlString));

            if (methodType == HttpHandler.HttpMethodType.POST || methodType == HttpHandler.HttpMethodType.PATCH) {
                requestBuilder.header("Content-Type", contentType);
            }

            if (jsonPayload != null) {
                requestBuilder.method(methodType.name(), HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));
            } else {
                requestBuilder.method(methodType.name(), HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Sending {} request to URL: {}", methodType, urlString);

            // Send the request
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (IOException e) {
            LOGGER.error("IOException while sending {} request to {}: {}", methodType, urlString, e.getMessage());
            throw new RuntimeException("Receiver is not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            LOGGER.error("InterruptedException while sending {} request to {}: {}", methodType, urlString, e.getMessage());
            throw new RuntimeException("Request was interrupted: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a POST request with dynamic content type to a specified URL.
     * This method is a wrapper for the generic {@link #sendRequest} method using POST as the HTTP method.
     *
     * @param urlString   The URL to send the POST request to.
     * @param jsonPayload The JSON payload to send.
     * @param contentType The Content-Type for the request (e.g., "application/json").
     * @return The server response as a string.
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the request is interrupted.
     */
    public static HttpResponse<String> sendPostRequest(String urlString, String jsonPayload, String contentType) throws IOException, InterruptedException {
        return sendRequest(urlString, jsonPayload, HttpHandler.HttpMethodType.POST, contentType);
    }

    /**
     * Sends a GET request to a specified URL.
     * This method is a wrapper for the generic {@link #sendRequest} method using GET as the HTTP method.
     *
     * @param urlString The URL to send the GET request to.
     * @return The server response as a string.
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the request is interrupted.
     */
    public static HttpResponse<String> sendGetRequest(String urlString) throws IOException, InterruptedException {
        return sendRequest(urlString, null, HttpHandler.HttpMethodType.GET, null);
    }

    /**
     * Sends a DELETE request with dynamic content type to a specified URL.
     * This method is a wrapper for the generic {@link #sendRequest} method using DELETE as the HTTP method.
     *
     * @param urlString   The URL to send the DELETE request to.
     * @param jsonPayload The JSON payload to send.
     * @param contentType The Content-Type for the request (e.g., "application/json").
     * @return The server response as a string.
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the request is interrupted.
     */
    public static HttpResponse<String> sendDeleteRequest(String urlString, String jsonPayload, String contentType) throws IOException, InterruptedException {
        return sendRequest(urlString, jsonPayload, HttpHandler.HttpMethodType.DELETE, contentType);
    }

    /**
     * Sends a PATCH request with dynamic content type to a specified URL.
     * This method is a wrapper for the generic {@link #sendRequest} method using PATCH as the HTTP method.
     *
     * @param urlString   The URL to send the PATCH request to.
     * @param jsonPayload The JSON payload to send.
     * @param contentType The Content-Type for the request (e.g., "application/json").
     * @return The server response as a string.
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the request is interrupted.
     */
    public static HttpResponse<String> sendPatchRequest(String urlString, String jsonPayload, String contentType) throws IOException, InterruptedException {
        return sendRequest(urlString, jsonPayload, HttpHandler.HttpMethodType.PATCH, contentType);
    }
}
