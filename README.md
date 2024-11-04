#EUREKA-HEARTBEAT-CLIENT

Project Name : eureka-heartbeat-client
Description : This Spring Boot application is specifically engineered to streamline the management of service instance registration and heartbeat scheduling through integration with a Eureka server. Its primary objectives encompass the seamless registration of service instances, ensuring they send periodic heartbeats, and facilitating their automatic deregistration should they become unavailable or unhealthy. The application operates within a distributed system environment, leveraging Eureka server capabilities to maintain real-time visibility and availability of registered services. This setup not only enhances system reliability but also supports efficient resource allocation and dynamic scaling of services based on real-time demands.
Java Version                 :  17
Spring Boot Version     :  3.3.1

#APPLICATION PROPERTIES 

application.properties

#REQUIRED CONFIGURATIONS

1.	HTTP Client Timeouts
•	http.client.connect-timeout-seconds
o	Specifies the connect timeout in seconds for HTTP client requests.
o	 Default is 10.
•	http.client.request-timeout-seconds
o	Specifies the request timeout in seconds for HTTP client requests. 
o	Default is 10.
2.	 Eureka Server URL
•	eureka.server.url
o	Specifies the URL of the Eureka server where service instances will register and send heartbeats.
o	 Default is http://localhost/localhostname.
3.	Service Instance App Name
•	service.instances.app.name
o	Specifies the name of the service instance.
o	 Default is eureka-server.
4.	Service Instances
•	service.instances
•	Specifies a comma-separated list of service instances. 
o	This list is empty by default and should be configured with actual service instance URLs.
5.	Health Check URL
•	service.instances.health.check.url
o	Specifies the endpoint or URL path for health checks relative to each service instance.
o	 Default is /actuator/health.
OPTIONAL CONFIGURATIONS
1.	Heartbeat Cron Expression
•	Heartbeat.cron : Specifies the cron expression for scheduling heartbeats.
o	 Default is 0 */5 * * * * (every 5 minutes).
2.	Eureka Apps Path
•	Eureka.apps.path : Specifies the path for Eureka applications.
o	 Default is /v2/apps/.

#AUTHENTICATION PARAMETERS
1.	Basic Authentication
•	basic.auth.username: Specifies the username for basic authentication with the Eureka server.
•	basic.auth.password: Specifies the password for basic authentication with the Eureka server.
•	basic.auth.header: Specifies the header value for basic authentication. This is typically set in the @PostConstruct method using the username and password.
These configurations ensure that the Register Service application can effectively manage service instance registration, perform health checks, and schedule heartbeats with flexibility and reliability. Adjustments to these properties allow customization based on specific deployment environments and requirements.
COMPONENTS AND KEY METHODS

RegisterPinotApplication.java

•	Main Method
o	The entry point for the Spring Boot application.
•	Annotations
o	@SpringBootApplication : Indicates a Spring Boot application.
o	@EnableScheduling : Enables scheduling for tasks, allowing periodic execution of methods.
HeartbeatScheduler.java
Initialization and Configuration
•	init() Method
o	Initializes HttpClient.
o	Parses Eureka server URLs from configuration.
o	Immediately schedules heartbeat upon initialization.
Instance Registration
•	registerInstance(String hostname, int port) Method
o	Registers a new service instance.
o	Uses POST method to send registration requests to the Eureka server
Heartbeat Scheduling
•	scheduleHeartbeat() Method
o	Scheduled method ( Configured via cron expression (${heartbeat.cron}).) to perform heartbeat checks. 
o	Iterates through configured service instances to check health and manage registrations.

Health Checks and Management
•	isInstanceUp(String instanceUrl,String healthCheckUrl) Method
o	Checks if a service instance is healthy using its health check URL.
•	isRegisteredInstance(String instanceUrl)
o	Checks if a service instance is registered with any configured Eureka server.
HTTP Request Handling
•	sendHttpRequest(String url, String method, String body) Method
o	Sends HTTP requests using HttpClient with configurable timeouts.
o	Supports GET, POST, PUT, DELETE methods with appropriate request bodies.
Instance Lifecycle Management
•	registerInstance(String hostname, int port)
o	Registers a new instance with all configured Eureka servers.
•	sendHeartbeat(String instanceUrl)
o	Sends a heartbeat for a registered instance to all configured Eureka servers.
•	deregisterInstance(String instance)
o	Deregisters an instance from all configured Eureka servers.
Error Handling and Logging
•	SLF4J Logging
o	Utilizes logging to provide detailed information on instance registration, health checks, and errors encountered during operation.




#DETAILED USE CASES

USE CASE I: REGISTERING A NEW SERVICE INSTANCE

Scenario : A new service instance Service-A needs to register with the Eureka server.
Steps :
1.	Configuration: Ensure eureka.server.url and service.instances.app.name are configured in application.properties.
2.	Endpoint: Use a POST request to /register.

{
    "ipAddr": "ipadress",
    "app": "Service-A",
    "hostName": "hostname",
    "port": {
        "$": "port",
        "@enabled": "true"
    },
    "healthCheckUrl": "healthcheckurl",
    "status": "UP",
    "dataCenterInfo": 
{ 
"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
      "name": "MyOwn"
 }
}

#USE CASE II : HEARTBEAT MANAGEMENT

Scenario : Periodic heartbeats are required to maintain the availability of registered instances.
Steps :
1.	Configuration : Set heartbeat.cron in application.properties for scheduling.
2.	Scheduled Task : Automatically triggers every 5 minutes (0 */5 * * * *).
3.	Health Check : Verifies each registered instance's health using its configured healthCheckUrl.
Outcome : The system ensures that only healthy instances remain registered, automatically deregistering any that fail health checks.
USE CASE III :  INSTANCE DE-REGISTRATION

Scenario : A service instance, Service-B, becomes unavailable and needs to be deregistered from the Eureka server.
Steps :
1.	Trigger : Detect unavailability of Service-B through failed health checks.
2.	Endpoint : Use a DELETE request to /deregister to remove Service-B.
Outcome : Service-B will be deregistered from the Eureka server, ensuring that it does not receive traffic.


