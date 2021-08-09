# Kafka Stream Failure Recovery
This application is used to demonstrate the behavior of a Kafka Streams application, in Spring Boot used together with Spring Cloud Stream library.


- [Scenario 0: successful processing of a message (null-hypothesis)](#scenario-0--successful-processing-of-a-message--null-hypothesis-)
- [Scenario 1: exception in the processor](#scenario-1--exception-in-the-processor)
- [Scenario 2: exception while deserializing](#scenario-2--exception-while-deserializing)
- [Scenario 3: Kafka unreachable](#scenario-3--kafka-unreachable)

## Scenario 0: successful processing of a message (null-hypothesis)
This is to determine the project has been correctly setup and is able to process messages from Kafka. If this scenario fails, the result of the other scenarios are invalidated.

### Code
See `Processor.successfulProcessor()`.
Topic: `successful-test`

### Test
In Kafka (local):

```shell
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic successful-test --property parse.key=true --property key.separator=":"
>key1:value1
>key2:value2
```

### Result
Application output (filtered on useful info):

```text
(successfulProcessor) Received record. Key=key1, Value=value1
(successfulProcessor) Received record. Key=key2, Value=value2
```

Processor logs messages and continues without failing. No `ERROR` logs.

## Scenario 1: exception in the processor
What happens when the code executed inside the processor's lambda function throws an Exception?

### Code
See `Processor.failingProcessor()`.
Topic: `failing-processor-test`

### Test
In Kafka (local):

```shell
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic failing-processor-test --property parse.key=true --property key.separator=":"
>key1:value1
>key2:value2
```

### Result
Application output (filtered on useful info):

```text
(failingProcessor) Received record. Key=key1, Value=value1
2021-08-04 13:55:20.693 ERROR 8564 --- [-StreamThread-1] o.a.k.s.processor.internals.TaskManager  : stream-thread [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1] Failed to process stream task 0_0 due to the following error:

org.apache.kafka.streams.errors.StreamsException: Exception caught in process. taskId=0_0, processor=KSTREAM-SOURCE-0000000000, topic=failing-processor-test, partition=0, offset=0, stacktrace=java.lang.RuntimeException: OH NO, I FAILED! Key=key1, Value=value1
	at failure.recovery.kafkastreams.Processor.lambda$failingProcessor$2(Processor.java:22)
	...
	
2021-08-04 13:55:20.694 ERROR 8564 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1] Encountered the following exception during processing and the thread is going to shut down: 

...
    
2021-08-04 13:55:20.694  INFO 8564 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1] State transition from RUNNING to PENDING_SHUTDOWN
2021-08-04 13:55:20.694  INFO 8564 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1] Shutting down
2021-08-04 13:55:20.696  INFO 8564 --- [-StreamThread-1] o.a.k.clients.consumer.KafkaConsumer     : [Consumer clientId=failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1-restore-consumer, groupId=null] Unsubscribed all topics or patterns and assigned partitions
2021-08-04 13:55:20.698  INFO 8564 --- [-StreamThread-1] o.a.k.clients.producer.KafkaProducer     : [Producer clientId=failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1-producer] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
2021-08-04 13:55:20.702  INFO 8564 --- [-StreamThread-1] o.a.k.clients.consumer.KafkaConsumer     : [Consumer clientId=failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1-restore-consumer, groupId=null] Unsubscribed all topics or patterns and assigned partitions
2021-08-04 13:55:20.708  INFO 8564 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6-StreamThread-1] State transition from PENDING_SHUTDOWN to DEAD
2021-08-04 13:55:20.709  INFO 8564 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6] State transition from RUNNING to ERROR
2021-08-04 13:55:20.709 ERROR 8564 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [failingProcessor-applicationId-5444a1ce-1041-45ca-b701-1b9a6786d4a6] All stream threads have died. The instance will be in error state and should be closed.
```

The processor for the `failing-processor-test` shuts down and is not automatically restarted. The second message is not received. 

The `successfulProcessor` remains to work, as it is still able to read messages from the `successful-test` topic:

```text
(successfulProcessor) Received record. Key=key3, Value=value3
```

## Scenario 2: exception while deserializing
What happens when the message on a Kafka topic cannot successfully be deserialized by the processor?

### Code
See `Processor.failingDeserializationProcessor()`. 
Topic: `failing-deserialization-test`

Additional properties:

```properties
# Processor bean expects value to be in String format, so should fail
spring.cloud.stream.kafka.streams.bindings.failingDeserializationProcessor-in-0.consumer.valueSerde=org.apache.kafka.common.serialization.Serdes$ByteBufferSerde
```

### Test
In Kafka (local):

```shell
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic failing-deserialization-test  --property parse.key=true   --property key.separator=":"
>key1:value1
>key2:value2
```

### Result
Application output (filtered on useful info):

```text
021-08-04 14:16:04.572 ERROR 28108 --- [-StreamThread-1] o.a.k.s.processor.internals.TaskManager  : stream-thread [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1] Failed to process stream task 0_0 due to the following error:

org.apache.kafka.streams.errors.StreamsException: ClassCastException invoking Processor. Do the Processor's input types match the deserialized types? Check the Serde setup and change the default Serdes in StreamConfig or provide correct Serdes via method parameters. Make sure the Processor can accept the deserialized input of type key: java.lang.String, and value: java.nio.HeapByteBuffer.
Note that although incorrect Serdes are a common cause of error, the cast exception might have another cause (in user code, for example). For example, if a processor wires in a store, but casts the generics incorrectly, a class cast exception could be raised during processing, but the cause would not be wrong Serdes.
	at ...
Caused by: java.lang.ClassCastException: class java.nio.HeapByteBuffer cannot be cast to class java.lang.String (java.nio.HeapByteBuffer and java.lang.String are in module java.base of loader 'bootstrap')

2021-08-04 14:16:04.572 ERROR 28108 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1] Encountered the following exception during processing and the thread is going to shut down: 

...

2021-08-04 14:16:04.572  INFO 28108 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1] State transition from RUNNING to PENDING_SHUTDOWN
2021-08-04 14:16:04.572  INFO 28108 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1] Shutting down
2021-08-04 14:16:04.574  INFO 28108 --- [-StreamThread-1] o.a.k.clients.consumer.KafkaConsumer     : [Consumer clientId=failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1-restore-consumer, groupId=null] Unsubscribed all topics or patterns and assigned partitions
2021-08-04 14:16:04.574  INFO 28108 --- [-StreamThread-1] o.a.k.clients.producer.KafkaProducer     : [Producer clientId=failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1-producer] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
2021-08-04 14:16:04.577  INFO 28108 --- [-StreamThread-1] o.a.k.clients.consumer.KafkaConsumer     : [Consumer clientId=failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1-restore-consumer, groupId=null] Unsubscribed all topics or patterns and assigned partitions
2021-08-04 14:16:04.583  INFO 28108 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5-StreamThread-1] State transition from PENDING_SHUTDOWN to DEAD
2021-08-04 14:16:04.583  INFO 28108 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5] State transition from RUNNING to ERROR
2021-08-04 14:16:04.583 ERROR 28108 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [failingDeserializationProcessor-applicationId-c318eee4-482c-4d42-8a95-92dcf2c6b4c5] All stream threads have died. The instance will be in error state and should be closed.
```

Same result as scenario 1: processor thread is shutdown and does not recover automatically. New messages are not received. This is the behavior as described in the documentation, as the `LogAndFailExceptionHandler` for serialization exceptions is used by default:

https://docs.spring.io/spring-cloud-stream-binder-kafka/docs/3.1.2/reference/html/kafka-streams.html#_error_handling


## Scenario 3: Kafka unreachable
How does the application behave if Kafka suddenly dies (aka shutdown)?

### Code
This is not processor or topic dependant, but this test will use the `Processor.successfulProcessor()` as a way to verify if the application was able to recover.

### Test

1. Start Kafka (via Docker Compose file in `/docker` directory)
2. Start the application (wait for it to start up)
3. Send control message, make sure it is processed successfully
4. Shutdown Kafka for 4 minutes
5. Observe application behavior
6. Start Kafka again
7. Observe application behavior

### Result
Application output (filtered on useful info):

```text
(successfulProcessor) Received record. Key=key4, Value=value4
2021-08-04 15:25:10.414  INFO 28912 --- [-StreamThread-1] o.a.kafka.clients.FetchSessionHandler    : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Error sending fetch request (sessionId=939577349, epoch=431) to node 1002:

org.apache.kafka.common.errors.DisconnectException: null

2021-08-04 15:25:10.414  INFO 28912 --- [-StreamThread-1] o.a.k.c.c.internals.AbstractCoordinator  : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Group coordinator localhost:9092 (id: 2147482645 rack: null) is unavailable or invalid, will attempt rediscovery
2021-08-04 15:25:14.473  WARN 28912 --- [99df2e1d9-admin] org.apache.kafka.clients.NetworkClient   : [AdminClient clientId=failingProcessor-applicationId-98eb15e2-1cc7-43e7-a9d4-b2b99df2e1d9-admin] Connection to node 1002 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
2021-08-04 15:25:14.582  WARN 28912 --- [read-1-producer] org.apache.kafka.clients.NetworkClient   : [Producer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-producer] Connection to node 1002 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
2021-08-04 15:25:15.192  WARN 28912 --- [-StreamThread-1] org.apache.kafka.clients.NetworkClient   : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Connection to node 1002 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
...
2021-08-04 15:29:25.840  WARN 28912 --- [-StreamThread-1] org.apache.kafka.clients.NetworkClient   : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Error while fetching metadata with correlation id 576 : {successful-test=UNKNOWN_TOPIC_OR_PARTITION}
2021-08-04 15:29:26.032  WARN 28912 --- [-StreamThread-1] org.apache.kafka.clients.NetworkClient   : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Error while fetching metadata with correlation id 577 : {successful-test=UNKNOWN_TOPIC_OR_PARTITION}
...
2021-08-04 15:29:26.712  INFO 28912 --- [-StreamThread-1] o.a.k.c.c.internals.AbstractCoordinator  : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Discovered group coordinator localhost:9092 (id: 2147482645 rack: null)
...
2021-08-04 15:29:27.421  INFO 28912 --- [-StreamThread-1] o.a.k.c.c.internals.ConsumerCoordinator  : [Consumer clientId=successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1-consumer, groupId=successfulProcessor-applicationId] Adding newly assigned partitions: 
2021-08-04 15:29:27.421  INFO 28912 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1] State transition from RUNNING to PARTITIONS_ASSIGNED
2021-08-04 15:29:27.421  INFO 28912 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9] State transition from RUNNING to REBALANCING
2021-08-04 15:29:27.472  INFO 28912 --- [-StreamThread-1] o.a.k.s.p.internals.StreamThread         : stream-thread [successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9-StreamThread-1] State transition from PARTITIONS_ASSIGNED to RUNNING
2021-08-04 15:29:27.472  INFO 28912 --- [-StreamThread-1] org.apache.kafka.streams.KafkaStreams    : stream-client [successfulProcessor-applicationId-73a2f82a-356b-43e8-9477-05fcc70640c9] State transition from REBALANCING to RUNNING
(successfulProcessor) Received record. Key=key5, Value=value5
```

We see that the application was initially able to receive a Kafka message. When Kafka was shutdown and thus unavailable, the application logs some exceptions and warnings, but does not shutdown but instead continues to make a connection. Once Kafka was up and running again, the application automatically recovered and was able to process new messages.