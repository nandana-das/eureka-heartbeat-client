package com.covacsis.ipf.pinot.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HeartbeatScheduler is a Spring component that manages the
 * heartbeat mechanism for service instances registered with Eureka.
 */
@Component
public class HeartbeatScheduler {
    private HttpClient httpClient;
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatScheduler.class.getName());

    @Value("${http.client.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${http.client.request-timeout-seconds:10}")
    private int requestTimeoutSeconds;

    @Value("${heartbeat.cron:0 */5 * * * *}")
    private String heartbeatCron;

    @Value("${eureka.server.url:http://localhost:8083/eureka}")
    private String eurekaServerUrls;

    @Value("${service.instances.app.name:eureka-server}")
    private String serviceInstancesAppName;

    @Value("${service.instances:}")
    private String serviceInstances;

    @Value("${service.instances.health.check.url:/actuator/health}")
    private String healthCheckUrl;

    @Value("${eureka.apps.path:/v2/apps/}")
    private String eurekaAppsPath;

    @Value("${eureka.server.health.check.url:/actuator/health}")
    private String serverHealthCheckUrl;

    @Value("${server.authentication.username:user}")
    private String serverUsername;

    @Value("${server.authentication.password:password}")
    private String serverPassword;

    @Value("${instance.authentication.username:user}")
    private String instanceUsername;

    @Value("${instance.authentication.password:password}")
    private String instancePassword;

    private List<String> eurekaServerUrlList;
    private String serverAuthHeader;
    private String instanceAuthHeader;

    /**
     * Initializes the HttpClient and parses the Eureka server URLs.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing HttpClient and parsing Eureka server URLs");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        this.eurekaServerUrlList = Arrays.asList(eurekaServerUrls.split(","));
        this.serverAuthHeader = "Basic " + Base64.getEncoder().encodeToString((serverUsername + ":" + serverPassword).getBytes());
        this.instanceAuthHeader = "Basic " + Base64.getEncoder().encodeToString((instanceUsername + ":" + instancePassword).getBytes());

    }

    /**
     * Scheduled task that checks the health of service instances,
     * registers them if they are up and not registered,
     * sends heartbeats to registered instances,
     * and deregisters instances that are down.
     */
    @Scheduled(cron = "${heartbeat.cron:0 */5 * * * *}")
    public void scheduleHeartbeat() {
        logger.debug("Running scheduleHeartbeat");
        List<String> instances = Arrays.asList(serviceInstances.split(","));

        instances.forEach(instanceUrl -> {
            try {
                URI uri = new URI(instanceUrl.trim());
                String hostname = uri.getHost();
                int port = uri.getPort();
                logger.debug("Performing health check for server: {}:{}", hostname, port);
                String instance = hostname + ":" + port;

                if (isInstanceUp(instanceUrl, healthCheckUrl,instanceAuthHeader)) {
                    try {
                        if (isInstanceRegistered(instance)) {
                            sendHeartbeat(instance);
                            logger.info("Instance {}:{} is up and heartbeat sent.", hostname, port);
                        } else {
                            registerInstance(hostname, port);
                        }
                    } catch (Exception e) {
                        logger.error("Error occurred while checking registration for instance {}:{}", instance, e.getMessage());
                    }
                } else {
                    deregisterInstance(instance);
                    logger.info("Instance {}:{} is down and deregistered.", hostname, port);
                }
            } catch (Exception e) {
                logger.error("Error occurred while processing instance " + instanceUrl, e);
            }
        });
    }

    /**
     * Checks if a service instance is up by performing a health check.
     *
     * @param instanceUrl   the URL of the instance
     * @param healthCheckUrl the health check endpoint
     * @return true if the instance is up, false otherwise
     */
    private boolean isInstanceUp(String instanceUrl, String healthCheckUrl,String authHeader) {
        logger.debug("Running isInstanceUp");
        try {
            String url = instanceUrl + healthCheckUrl;
            HttpResponse<String> response = sendHttpRequest(url, "GET", null, instanceAuthHeader);

            logger.debug("Health check response status code for {}: {}", url, response.statusCode());
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                if (responseBody != null && responseBody.toLowerCase().contains("\"status\":\"up\"")) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred during health check for instance {}: {}", instanceUrl, e.getMessage());
        }
        return false;
    }

    /**
     * Sends an HTTP request to the specified URL.
     *
     * @param url    the URL to send the request to
     * @param method the HTTP method (GET, POST, etc.)
     * @param body   the request body (can be null)
     * @param authHeader the authorization header value
     * @return the HttpResponse object
     * @throws Exception if an error occurs during the request
     */
    private HttpResponse<String> sendHttpRequest(String url, String method, String body, String authHeader) throws Exception {
        logger.debug("Running sendHttpRequest");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));

        if (requestTimeoutSeconds > 0) {
            requestBuilder.timeout(Duration.ofSeconds(requestTimeoutSeconds));
        }

        HttpRequest request = requestBuilder.build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Checks if a service instance is registered with Eureka.
     *
     * @param instanceUrl the URL of the instance
     * @return true if the instance is registered, false otherwise
     * @throws Exception if an error occurs during the registration check
     */
    private boolean isInstanceRegistered(String instanceUrl) throws Exception {
        logger.debug("Running isRegisteredInstance");

        for (String eurekaServerUrl : eurekaServerUrlList) {
            try {
                String serverUrl = eurekaServerUrl.substring(0, eurekaServerUrl.lastIndexOf("/eureka"));
                if (isInstanceUp(serverUrl, serverHealthCheckUrl,serverAuthHeader)) {
                    String url = eurekaServerUrl + eurekaAppsPath + serviceInstancesAppName + "/" + instanceUrl;
                    logger.debug("Checking registration at: {}", url);
                    HttpResponse<String> response = sendHttpRequest(url, "GET", null, serverAuthHeader);
                    if (response.statusCode() == 200) {
                        logger.info("Instance {} is registered at server {}", instanceUrl, eurekaServerUrl);
                        return true;
                    } else {
                        logger.debug("Instance {} not registered at server {}. Status code: {}", instanceUrl, eurekaServerUrl, response.statusCode());
                        return false;
                    }
                } else {
                    logger.info("Server {} is down. Searching next server.", eurekaServerUrl);
                }
            } catch (Exception e) {
                logger.error("Error occurred while checking registration for instance {} on server {}: {}", instanceUrl, eurekaServerUrl, e.getMessage());
            }
        }

        logger.error("No available Eureka servers found for instance {}", instanceUrl);
        throw new Exception("No available Eureka servers found for instance " + instanceUrl);
    }

    /**
     * Registers a service instance with Eureka.
     *
     * @param hostname the hostname of the instance
     * @param port     the port number of the instance
     * @return true if the registration is successful, false otherwise
     */
    private boolean registerInstance(String hostname, int port) {
        logger.debug("Running registerInstance");
        for (String eurekaServerUrl : eurekaServerUrlList) {
            try {
                String url = eurekaServerUrl + eurekaAppsPath + serviceInstancesAppName;
                String requestBody = buildRegistrationRequestBody(hostname, port);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", instanceAuthHeader)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                if (requestTimeoutSeconds > 0) {
                    requestBuilder.timeout(Duration.ofSeconds(requestTimeoutSeconds));
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                logger.debug("Registration response status code: {}", response.statusCode());
                if (response.statusCode() == 204) {
                    logger.info("Instance {}:{} is registered.", hostname, port);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error occurred while registering instance on server {}: {}", eurekaServerUrl, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Deregisters a service instance from Eureka.
     *
     * @param instanceUrl the URL of the instance
     * @return true if the deregistration is successful, false otherwise
     */
    private boolean deregisterInstance(String instanceUrl) {
        logger.debug("Running deregisterInstance");
        for (String eurekaServerUrl : eurekaServerUrlList) {
            try {
                String url = eurekaServerUrl + eurekaAppsPath + serviceInstancesAppName + "/" + instanceUrl;
                HttpResponse<String> response = sendHttpRequest(url, "DELETE", null, serverAuthHeader);
                logger.debug("Deregistration response status code: {}", response.statusCode());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error occurred while deregistering instance on server {}: {}", eurekaServerUrl, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Sends a heartbeat to a service instance registered with Eureka.
     *
     * @param instanceUrl the URL of the instance
     * @return true if the heartbeat is successful, false otherwise
     */
    private boolean sendHeartbeat(String instanceUrl) {
        logger.debug("Running sendHeartbeat");
        for (String eurekaServerUrl : eurekaServerUrlList) {
            try {
                String url = eurekaServerUrl + eurekaAppsPath + serviceInstancesAppName + "/" + instanceUrl + "/status?value=UP";
                HttpResponse<String> response = sendHttpRequest(url, "PUT", null, serverAuthHeader);
                logger.debug("Heartbeat response status code: {}", response.statusCode());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error occurred while sending heartbeat to server {}: {}", eurekaServerUrl, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Builds the registration request body for a service instance.
     *
     * @param hostname the hostname of the instance
     * @param port     the port number of the instance
     * @return the registration request body as a JSON string
     */
    private String buildRegistrationRequestBody(String hostname, int port) {
        return String.format(
                "{" +
                        "  \"instance\": {" +
                        "    \"ipAddr\": \"%s\"," +
                        "    \"app\": \"%s\"," +
                        "    \"hostName\": \"%s\"," +
                        "    \"port\": {" +
                        "      \"$\": %d," +
                        "      \"@enabled\": true" +
                        "    }," +
                        "    \"healthCheckUrl\": \"%s\"," +
                        "    \"status\": \"UP\"," +
                        "    \"dataCenterInfo\": {" +
                        "      \"@class\": \"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo\"," +
                        "      \"name\": \"MyOwn\"" +
                        "    }" +
                        "  }" +
                        "}",
                hostname, serviceInstancesAppName, hostname, port, healthCheckUrl);
    }
}
