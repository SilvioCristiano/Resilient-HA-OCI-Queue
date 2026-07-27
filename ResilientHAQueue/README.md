# Resilient HA with OCI Queue and Spring Boot

Reference implementation for a pre-provisioned primary/secondary OCI Queue topology. It uses OCI Queue consumer groups for native fan-out, while preserving an active/passive regional failover path.

## What changed

- Uses one current, consistent OCI Java SDK version (3.91.1), which supports `consumerGroupId` in get/delete calls.
- Has typed external configuration under `oci.queue.*`; OCI config path, OCIDs and endpoints are supplied through environment variables, never source code or a mutable properties file.
- Publishes message attributes through `MessageMetadata.customProperties`, allowing OCI consumer-group filters such as `:eventType = "order.created" AND :region = "sa-saopaulo-1"`.
- Runs long polling in a dedicated lifecycle worker; business processing completes before `DeleteMessage`, so failures are eligible for OCI redelivery and the queue DLQ policy.
- Adds bounded exponential backoff with jitter, structured logs, and Micrometer counters.
- Removes runtime queue creation. Provision queues, DLQs, consumer groups, filters, and IAM with Terraform/OCI Console before deploying the application.

## Configure

At a minimum, set these environment variables:

```bash
export OCI_QUEUE_PRIMARY_ID='ocid1.queue...'
export OCI_QUEUE_PRIMARY_ENDPOINT='https://cell-1.queue.messaging.<region>.oci.oraclecloud.com'
export OCI_QUEUE_SECONDARY_ID='ocid1.queue...'
export OCI_QUEUE_SECONDARY_ENDPOINT='https://cell-1.queue.messaging.<region>.oci.oraclecloud.com'
export OCI_QUEUE_PRIMARY_CONSUMER_GROUP_ID='ocid1.queueconsumergroup...'
export OCI_QUEUE_CONSUMER_ENABLED=true
```

Leave `OCI_QUEUE_*_CONSUMER_GROUP_ID` empty to use the queue's Primary Consumer Group. Use the OCID of each filtered consumer group when a service must receive its own fan-out delivery path. Keep the Primary Consumer Group enabled unless messages deliberately may be dropped when no filter matches.

`OCI_CONFIG_FILE` and `OCI_CONFIG_PROFILE` optionally select the local OCI SDK profile; by default they are `~/.oci/config` and `DEFAULT`.

## Publish an event

Inject `QueuePublisher` and publish a `QueueEvent`:

```java
publisher.publish(new QueueEvent(
    "{\"orderId\":\"o-123\"}",
    Map.of("eventType", "order.created", "region", "sa-saopaulo-1", "businessKey", "o-123")
));
```

Attribute keys and values are case-sensitive, and OCI evaluates filters only at publish time. Do not route on the JSON body.

## Production checklist

- Make the `MessageHandler` business transaction idempotent using `businessKey` (for example, a unique DB constraint/outbox), because at-least-once delivery is expected.
- Set queue visibility, max delivery attempts, retention, and a DLQ according to the longest processing time.
- Grant producers `queue-push` and consumers `queue-pull`; do not give the runtime service queue-administration permissions.
- Alarm on queue backlog, DLQ depth, consumer errors, and OCI's dropped-message metric for fan-out.

Run the checks with Java 17+: `JAVA_HOME=/path/to/jdk17 sh ./mvnw test`.

## Prerequisites

- JDK 17 (the build uses Spring Boot 3.3 and Java 17).
- OCI tenancy, compartment and a user or workload identity with access to Queue.
- A primary queue and a secondary queue already provisioned, including their queue endpoints.
- Consumer groups created in OCI Queue when fan-out filtering is required. The Primary Consumer Group can be used by leaving the group OCID empty.
- A configured local OCI SDK profile in `~/.oci/config`, or an alternative file supplied through `OCI_CONFIG_FILE`.
- Maven is not required globally: the repository includes the Maven Wrapper (`mvnw`).

For a production deployment, provision queues, consumer groups, filters, DLQ policy, alarms and IAM with Terraform or the OCI Console before starting the application. The application intentionally does not create or modify these OCI resources at runtime.

## Quick configuration

From the `ResilientHAQueue` directory, configure the OCI profile and queue values:

```bash
export OCI_CONFIG_FILE="$HOME/.oci/config"
export OCI_CONFIG_PROFILE=DEFAULT
export OCI_QUEUE_PRIMARY_ID='ocid1.queue...'
export OCI_QUEUE_PRIMARY_ENDPOINT='https://cell-1.queue.messaging.<region>.oci.oraclecloud.com'
export OCI_QUEUE_SECONDARY_ID='ocid1.queue...'
export OCI_QUEUE_SECONDARY_ENDPOINT='https://cell-1.queue.messaging.<region>.oci.oraclecloud.com'
export OCI_QUEUE_PRIMARY_CONSUMER_GROUP_ID='ocid1.queueconsumergroup...'
export OCI_QUEUE_SECONDARY_CONSUMER_GROUP_ID='ocid1.queueconsumergroup...'
export OCI_QUEUE_CONSUMER_ENABLED=true
```

Then validate the build and start the worker:

```bash
./mvnw test
./mvnw spring-boot:run
```

The consumer starts only when `OCI_QUEUE_CONSUMER_ENABLED=true`. Keep it `false` when validating only configuration or when the instance must act only as a publisher.

## Running in Eclipse

1. Open **File > Import > Maven > Existing Maven Projects** and select the `ResilientHAQueue` directory.
2. Configure Eclipse to use a Java 17 JDK: **Window > Preferences > Java > Installed JREs**.
3. In **Run > Run Configurations > Spring Boot App** (or **Java Application**), select `com.playbook.ai.RestServiceApplication`.
4. Add the variables from [Quick configuration](#quick-configuration) on the **Environment** tab. Do not put OCIDs or private-key paths in `application.properties`.
5. Run the application. Use the Console view to confirm the consumer has started and to inspect structured application logs.

If the project was previously imported with another JDK, run **Maven > Update Project** after switching to Java 17.

## Configuration variables

The following values are read from environment variables. Their resolved defaults are defined in `src/main/resources/application.properties`.

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `OCI_CONFIG_FILE` | No | `~/.oci/config` | OCI SDK configuration file. |
| `OCI_CONFIG_PROFILE` | No | `DEFAULT` | Profile within the OCI configuration file. |
| `OCI_QUEUE_PRIMARY_ID` | Yes | — | OCID of the preferred queue. |
| `OCI_QUEUE_PRIMARY_ENDPOINT` | Yes | — | Messaging endpoint of the preferred queue. |
| `OCI_QUEUE_PRIMARY_CONSUMER_GROUP_ID` | No | Empty | Consumer Group OCID for the primary queue; empty uses its Primary Consumer Group. |
| `OCI_QUEUE_SECONDARY_ID` | Yes | — | OCID of the pre-provisioned failover queue. |
| `OCI_QUEUE_SECONDARY_ENDPOINT` | Yes | — | Messaging endpoint of the failover queue. |
| `OCI_QUEUE_SECONDARY_CONSUMER_GROUP_ID` | No | Empty | Consumer Group OCID for the secondary queue. |
| `OCI_QUEUE_CONSUMER_ENABLED` | No | `false` | Enables the background long-polling consumer. |
| `OCI_QUEUE_BATCH_SIZE` | No | `10` | Maximum messages requested per poll (1–100). |
| `OCI_QUEUE_POLL_TIMEOUT_SECONDS` | No | `30` | OCI Queue long-poll timeout (0–30 seconds). |
| `OCI_QUEUE_VISIBILITY_TIMEOUT_SECONDS` | No | `60` | Time available to process a message before it is visible again. |
| `OCI_QUEUE_RETRY_MAX_ATTEMPTS` | No | `3` | Attempts for a transient publish failure before failover. |
| `OCI_QUEUE_RETRY_INITIAL_DELAY_MS` | No | `250` | Initial exponential-backoff delay. |
| `OCI_QUEUE_RETRY_MAX_DELAY_MS` | No | `5000` | Maximum backoff delay before jitter is applied. |

The primary and secondary queue identifiers/endpoints are mandatory even when the consumer is disabled, because startup validates the HA topology configuration.

## Testing through the OCI Console

Use the Console to validate routing and consumption without adding a REST endpoint to this reference implementation.

1. Start the application with `OCI_QUEUE_CONSUMER_ENABLED=true` and a primary consumer-group OCID configured.
2. In the OCI Console, open the primary queue and use **Produce message** (or the equivalent message-publish action).
3. Publish a small JSON body, for example `{"orderId":"o-123"}`, and add message attributes such as `eventType=order.created`, `region=sa-saopaulo-1` and `businessKey=o-123`.
4. Ensure the attributes satisfy the consumer group's OCI filter. Filters are evaluated during publishing, not while polling.
5. Confirm in the Eclipse/terminal logs that the handler processed the message and that it was acknowledged only after successful handling.
6. To validate redelivery, make the `MessageHandler` throw an exception in a non-production environment. Do not acknowledge the message manually; let the visibility timeout and max-delivery policy drive redelivery/DLQ routing.

For fan-out validation, configure two independent consumer groups with different filters, publish one message with matching attributes, and confirm each matching group receives its own delivery.

## Practices implemented

- **Native fan-out:** producer attributes are sent in OCI `MessageMetadata.customProperties`, so routing is performed by OCI Consumer Group filters.
- **At-least-once consumption:** `DeleteMessage` occurs only after `MessageHandler` completes successfully.
- **Idempotency boundary:** messages carry a `businessKey`; the business handler must enforce idempotency through its database/outbox transaction or equivalent durable control.
- **Long polling:** a dedicated `SmartLifecycle` worker performs queue I/O outside controller/request threads.
- **Bounded retry:** publisher retries transient `408`, `429` and `5xx` OCI failures with exponential backoff and jitter.
- **Regional fallback:** after primary publish/poll failure, the component attempts the pre-provisioned secondary queue.
- **Controlled failure handling:** processing failures are logged and left unacknowledged for OCI redelivery or queue-managed DLQ routing.
- **Externalized configuration:** queue OCIDs, endpoints and OCI profile selection come from environment variables rather than source code.

## Observability

The application publishes Micrometer counters that can be exported by the Actuator/Micrometer integration used in the target environment:

| Metric | Meaning |
| --- | --- |
| `oci.queue.messages.published` | Messages successfully accepted by OCI Queue. |
| `oci.queue.messages.publish.failed` | Publish operations that exhausted retries. |
| `oci.queue.messages.consumed` | Messages acknowledged after successful handling. |
| `oci.queue.messages.processing.failed` | Handler failures left for OCI redelivery or DLQ processing. |

The project exposes `health`, `info` and `metrics` through Spring Boot Actuator configuration. As the sample runs with `spring.main.web-application-type=none`, use logs, JMX or a configured metrics exporter for runtime collection; it does not start an HTTP management server by itself.

Create OCI Monitoring alarms for queue backlog, DLQ depth, consumer errors, publish failures and the fan-out dropped-message metric. A sustained difference between published and consumed counts is a signal to inspect filters, consumer availability, visibility timeout and DLQ policy.

## Troubleshooting

| Symptom | Likely cause and action |
| --- | --- |
| Application fails during startup with validation errors | Set the primary and secondary queue OCIDs/endpoints. Check for empty or malformed environment variables. |
| `401` or `403` from OCI | Confirm the selected OCI profile, key material and IAM policy. Producers need `queue-push`; consumers need `queue-pull`. |
| Consumer is running but receives no messages | Verify `OCI_QUEUE_CONSUMER_ENABLED=true`, the selected consumer-group OCID, message attributes and the group's filter. An unmatched fan-out message can be dropped. |
| Messages reappear after processing | Ensure the handler completes within the visibility timeout and commits its business transaction before returning. Increase visibility timeout when necessary. |
| Repeated processing of the same event | This is valid at-least-once behavior. Use `businessKey` with a durable idempotency control. |
| Messages go to DLQ | Inspect handler failures, max-delivery policy, filter design and visibility timeout. Replay only after correcting the root cause. |
| Secondary queue is used unexpectedly | Check primary endpoint availability, OCI service health, DNS/network rules and the logged primary failure before treating it as a failover event. |

## Security

- Never commit `~/.oci/config`, private keys, OCIDs used by an environment, or a populated `queue.properties` file.
- Prefer OCI Instance Principals, Workload Identity or a managed secret store for deployed workloads; local OCI config is best suited to development.
- Apply least privilege: runtime producers receive `queue-push`, runtime consumers receive `queue-pull`, and only provisioning automation receives administration permissions.
- Keep queue endpoints and API traffic protected by OCI networking controls; use private access where the deployment topology supports it.
- Treat message attributes as routing metadata, not a place for confidential data. Keep sensitive payloads encrypted and apply appropriate data-retention/DLQ access controls.
- Rotate OCI API keys and secrets according to the organization's policy, and audit IAM policy changes and DLQ access.

## Useful commands

Run these commands from `ResilientHAQueue`:

```bash
# Confirm the Java version used by the build
java -version

# Run unit tests
./mvnw test

# Build the executable JAR
./mvnw clean package

# Start with Maven (uses the current environment variables)
./mvnw spring-boot:run

# Start the packaged application
java -jar target/resilient-ha-oci-queue-1.0.0-SNAPSHOT.jar

# Inspect the effective Maven dependency tree
./mvnw dependency:tree

# Disable polling temporarily while keeping the same queue configuration
OCI_QUEUE_CONSUMER_ENABLED=false ./mvnw spring-boot:run
```
