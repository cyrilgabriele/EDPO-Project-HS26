package com.examples;


import com.data.Clicks;
import com.google.common.io.Resources;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.Node;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class ClicksProducer {

    public static void main(String[] args) throws Exception {

        // Specify Topic
        String topic = "click-events";

        // Read Kafka properties file
        Properties properties;
        try (InputStream props = Resources.getResource("producer.properties").openStream()) {
            properties = new Properties();
            properties.load(props);
        }

        // Create Kafka producer
        KafkaProducer<String, Clicks> producer = producer = new KafkaProducer<>(properties);

        /// delete existing topic with the same name
        deleteTopic(topic, properties);

        // create new topic with 1 partition and replication factor of 3
        createTopic(topic, 1, 3, properties);

        int previousLeaderId = -1;

        try {

            // Define a counter which will be used as an eventID
            int counter = 0;

            // Create AdminClient once outside the loop for monitoring
            AdminClient adminClient = AdminClient.create(properties);


            // Variable to track changes in ISR (so we don't print on every loop if nothing changed)
            String previousIsrString = "";
            String previousClusterNodes = "";

            while(true) {

                 // Monitor the Leader using AdminClient (Forces a network check)
                try {
                    Collection<Node> clusterNodes = adminClient.describeCluster().nodes().get();
                    String currentClusterNodes = clusterNodes.stream()
                            .sorted(Comparator.comparingInt(Node::id))
                            .map(node -> node.id() + "@" + node.host() + ":" + node.port())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    if (!currentClusterNodes.equals(previousClusterNodes)) {
                        System.out.println("Cluster nodes: [" + currentClusterNodes + "]");
                        previousClusterNodes = currentClusterNodes;
                    }

                    Map<String, org.apache.kafka.clients.admin.TopicDescription> topicDescription = 
                        adminClient.describeTopics(Collections.singleton(topic)).all().get();
                    
                    org.apache.kafka.clients.admin.TopicDescription desc = topicDescription.get(topic);
                    org.apache.kafka.common.TopicPartitionInfo partitionInfo = desc.partitions().get(0);
                    Node leader = partitionInfo.leader();
                    List<Node> isr = partitionInfo.isr();

                    // Convert List<Node> to int[] for printing
                    int[] inSyncReplicas = isr.stream().mapToInt(Node::id).toArray();
                    String currentIsrString = Arrays.toString(inSyncReplicas);

                    // Check if Leader OR ISR has changed
                    if ((leader != null && leader.id() != previousLeaderId) || !currentIsrString.equals(previousIsrString)) {
                         
                         int leaderId = (leader != null) ? leader.id() : -1;
                         String leaderHost = (leader != null) ? leader.host() : "unknown";
                         int leaderPort = (leader != null) ? leader.port() : -1;
                         System.out.println("Current Leader: " + leaderId+ " host: "+leaderHost+ " port: "+leaderPort);
                         System.out.println("In Sync Replicates: " + currentIsrString);
                         
                         previousLeaderId = leaderId;
                         previousIsrString = currentIsrString;

                    } else if (leader == Node.noNode()) { 
                         System.out.println("NO LEADER currently available!");
                         previousLeaderId = -1;
                    }
                } catch (Exception e) {
                    System.out.println("Cluster is unreachable or Leader is down.");
                    previousLeaderId = -1;
                }

                // sleep for a random time interval between 500 ms and 5000 ms
                try {
//                    Thread.sleep(getRandomNumber(500, 5000));
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // generate a random click event using constructor  Clicks(int eventID, long timestamp, int xPosition, int yPosition, String clickedElement)
                Clicks clickEvent = new Clicks(counter,System.nanoTime(), getRandomNumber(0, 1920), getRandomNumber(0, 1080), "EL"+getRandomNumber(1, 20));

                // send the click event
                producer.send(new ProducerRecord<String, Clicks>(
                        topic, // topic
                        clickEvent  // value
                ));

                // print to console
                System.out.println("clickEvent sent: "+clickEvent.toString());

                // increment counter i.e., eventID
                counter++;

            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        } finally {
            producer.close();
        }


    }


    /*
    Generate a random nunber
    */
    private static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    /*
    Create topic
     */
    private static void createTopic(String topicName, int numPartitions, int numReplicates, Properties properties) throws Exception {

        AdminClient admin = AdminClient.create(properties);

        //checking if topic already exists
        boolean alreadyExists = admin.listTopics().names().get().stream()
                .anyMatch(existingTopicName -> existingTopicName.equals(topicName));
        if (alreadyExists) {
            System.out.printf("topic already exits: %s%n", topicName);
        } else {
            //creating new topic
            System.out.printf("creating topic: %s%n", topicName);
            NewTopic newTopic = new NewTopic(topicName, numPartitions, (short) numReplicates); // 
            admin.createTopics(Collections.singleton(newTopic)).all().get();
        }
    }

    /*
    Delete topic
     */
    private static void deleteTopic(String topicName, Properties properties) {
        try (AdminClient client = AdminClient.create(properties)) {
            DeleteTopicsResult deleteTopicsResult = client.deleteTopics(Collections.singleton(topicName));
            while (!deleteTopicsResult.all().isDone()) {
                // Wait for future task to complete
            }
        }
    }

}
