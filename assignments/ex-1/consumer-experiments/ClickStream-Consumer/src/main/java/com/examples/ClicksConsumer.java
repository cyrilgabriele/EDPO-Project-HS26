package com.examples;



import com.google.common.io.Resources;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import static org.testng.Assert.assertEquals;

public class ClicksConsumer {
    public static void main(String[] args) throws IOException, ParseException {

        // Read Kafka properties file and create Kafka consumer with the given properties
        KafkaConsumer<String, Object> consumer;
        try (InputStream props = Resources.getResource("consumer.properties").openStream()) {
            Properties properties = new Properties();
            properties.load(props);
            consumer = new KafkaConsumer<>(properties);
        }

        // subscribe to relevant topics
        consumer.subscribe(Arrays.asList("click-events"));


        while (true) {

            // pool new data
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(8));

            // process consumer records depending on record.topic() and record.value()
            for (ConsumerRecord<String, Object> record : records) {
                // switch/case
                switch (record.topic()) {
                    case "click-events":
                        System.out.println("Received click-events - value: " + record.value()+ "- partition: "+record.partition());

                        break;

                    default:
                        throw new IllegalStateException("Shouldn't be possible to get message on topic " + record.topic());
                }
            }


        }
    }

}

