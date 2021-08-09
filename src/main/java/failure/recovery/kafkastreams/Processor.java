package failure.recovery.kafkastreams;

import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Processor {

    @Bean
    public java.util.function.Consumer<KStream<String, String>> successfulProcessor() {
        return input ->
                input.foreach((key, value) ->
                        System.out.println("(successfulProcessor) Received record. Key=" + key + ", Value=" + value));
    }

    @Bean
    public java.util.function.Consumer<KStream<String, String>> failingProcessor() {
        return input ->
                input.foreach((key, value) -> {
                    System.out.println("(failingProcessor) Received record. Key=" + key + ", Value=" + value);
                    throw new RuntimeException("OH NO, I FAILED! Key=" + key + ", Value=" + value);
                });

    }

    /**
     * Expects the key and value to have the String type, but in configuration a ByteBufferSerde is configured for the value.
     */
    @Bean
    public java.util.function.Consumer<KStream<String, String>> failingDeserializationProcessor() {
        return input ->
                input.foreach((key, value) ->
                        System.out.println("(failingDeserializationProcessor) Received record. Key=" + key + ", Value=" + value));
    }
}
