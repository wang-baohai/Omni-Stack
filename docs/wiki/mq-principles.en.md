# 1. Unified MQ management: understand “process it later”

Some follow-up work should not keep a user waiting, such as delivering operation records to a shared query service. A message queue (MQ) lets the producer leave work for another component to handle later. The two sides need not be available at the same instant, but waiting, failures, duplicates, and investigation still need designs.

## Think of sending a parcel

| Term | Analogy | Example in this project |
|---|---|---|
| Producer | The sender creating work | A business service or operation-log producer |
| Broker | The station receiving and forwarding parcels | RocketMQ |
| Consumer | The recipient doing the work | The operation-log consumer in Base |
| Outbox | The sender's own list of parcels to send | Each service's local sys_mq_message |
| Relay | The worker picking up and sending parcels | The message relay triggered by XXL-JOB |

Outbox is not a queue inside the Broker, and the management page is not the transport path. It displays sending records, retries, and dead letters; completed business work still requires checking the consumer result.

## Why record an intention first?

Saving business data and then sending directly can leave committed data with a failed send. Sending first can leave a delivered message with rolled-back business data. Outbox lets business data and the intention to send commit together in one effective local transaction, with sending performed later.

The preconditions matter: the same data source, one effective transaction, and a call through the appropriate proxy. A method named send alone proves no atomicity. The operation-log producer also catches sending exceptions, so it cannot provide this guarantee for every business path.

## Reading order

Continue with [tools and responsibilities](mq-design.en.md), [Outbox persistence](mq-outbox.en.md), [relay and idempotent consumption](mq-relay.en.md), [management and recovery](mq-practice.en.md), then [extension and verification](mq-extension.en.md). Relevant source is included directly.

Remember: enqueued is not sent, sent is not consumed, and reliable delivery does not mean duplicates are impossible.
