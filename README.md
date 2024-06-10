## Kafka-Streams-Orders-Processing

Stream processing application for orders, utilizing key features of Kafka Streams in a realistic scenario to demonstrate a comprehensive, end-to-end implementation.

## Requirements
- JDK 1.8.
- Recommended SBT 1.6.2.
- Docker & Docker Compose.
## Running the Kafka broker on a docker container
- While on main -> Docker -> kafka directory run :
```bash
docker-compose up
```
- After succefully making kafka container up and running, open an interactive terminal session inside a running Docker container in our case named broker
```bash
docker exec -it broker bash
```
Now, create the topics that we are going use in our KafkaStreams application : 
```bash
kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic orders-by-user \
  --create

kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic discount-profiles-by-user \
  --create \
  --config "cleanup.policy=compact"

kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic discounts \
  --create \
  --config "cleanup.policy=compact"

kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --create

kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic payments \
  --create

kafka-topics \
  --bootstrap-server localhost:9092 \
  --topic paid-orders \
  --create
```
- Run the KafkaStreams application : 
```bash
sbt run
```
