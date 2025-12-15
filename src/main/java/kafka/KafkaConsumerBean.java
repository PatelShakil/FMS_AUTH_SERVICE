package kafka;

import com.shakilpatel.auth.service.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Singleton
@Startup
public class KafkaConsumerBean {

    private static final Logger LOGGER = Logger.getLogger(KafkaConsumerBean.class.getName());

    @PersistenceContext(unitName = "authPU")
    private EntityManager em;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    private String bootstrapServers;

    @ConfigProperty(name = "kafka.consumer.group.id")
    private String groupId;

    @ConfigProperty(name = "kafka.consumer.topic.org-created")
    private String orgCreatedTopic;

    @ConfigProperty(name = "kafka.consumer.topic.org-approved")
    private String orgApprovedTopic;

    private KafkaConsumer<String, String> consumer;
    private Jsonb jsonb;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        bootstrapServers = ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
        groupId = ConfigProvider.getConfig().getValue("kafka.consumer.group.id", String.class);
        orgCreatedTopic = ConfigProvider.getConfig().getValue("kafka.consumer.topic.org-created", String.class);
        orgApprovedTopic = ConfigProvider.getConfig().getValue("kafka.consumer.topic.org-approved", String.class);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(orgCreatedTopic, orgApprovedTopic));
        jsonb = JsonbBuilder.create();
        running.set(true);

        LOGGER.info("✅ Kafka Consumer initialized - auth-service");
    }

    @Schedule(hour = "*", minute = "*", second = "*/5", persistent = false)
    public void pollMessages() {
        if (!running.get()) {
            return;
        }

        try {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                LOGGER.info("📨 Received from topic: " + record.topic());

                if (record.topic().equals(orgCreatedTopic)) {
                    handleOrgCreated(record.value());
                } else if (record.topic().equals(orgApprovedTopic)) {
                    handleOrgApproved(record.value());
                }
            }
        } catch (Exception e) {
            LOGGER.severe("❌ Error polling Kafka: " + e.getMessage());
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void handleOrgCreated(String message) {
        try {
            Map<String, Object> event = jsonb.fromJson(message, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();
            Long orgId = ((Number) event.get("orgId")).longValue();

            LOGGER.info("🏢 Org created: orgId=" + orgId + ", userId=" + userId);

            // Update user.org_id
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setOrgId(orgId);
                em.merge(user);
                LOGGER.info("✅ User " + userId + " linked to org " + orgId);
            }
        } catch (Exception e) {
            LOGGER.severe("❌ Error handling org.created: " + e.getMessage());
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void handleOrgApproved(String message) {
        try {
            Map<String, Object> event = jsonb.fromJson(message, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();

            LOGGER.info("✅ Org approved for userId: " + userId);

            // Activate user
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setIsActive(true);
                em.merge(user);
                LOGGER.info("✅ User " + userId + " activated");

                // TODO: Send welcome email via notification-service
            }
        } catch (Exception e) {
            LOGGER.severe("❌ Error handling org.approved: " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        running.set(false);
        if (consumer != null) {
            consumer.close();
        }
        if (jsonb != null) {
            try {
                jsonb.close();
            } catch (Exception ignored) {
            }
        }
    }
}
