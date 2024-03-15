package org.openremote.manager.mqtt.gateway;

import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.mqtt.Topic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.openremote.manager.event.ClientEventService.HEADER_CONNECTION_TYPE;
import static org.openremote.manager.mqtt.MQTTBrokerService.getConnectionIDString;
import static org.openremote.model.Constants.REALM_PARAM_NAME;
import static org.openremote.model.Constants.SESSION_KEY;


/**
 * Base class for MQTT Publish Topic Handlers.
 * Subclasses need to define topic handler methods annotated with {@link MQTTPublishTopic}
 * MQTTHandler classes can use this to associate handler functions with their publish topic handlers
 */
abstract class MQTTPublishTopicHandler {

    private static final Logger LOG = Logger.getLogger(MQTTPublishTopicHandler.class.getName());
    private final HashMap<Topic, MQTTMessageHandler> handlers;


    MQTTPublishTopicHandler() {
        LOG.info("Initializing MQTT Publish Topic Handler");
        this.handlers = initHandlers();

        if (!this.handlers.isEmpty()) {
            this.handlers.forEach((topic, handler) -> LOG.info("Registered handler for topic " + topic));
        }
    }

    /**
     * Initializes the handlers for the class by scanning for methods annotated with {@link MQTTPublishTopic}
     * and creating a map of topic, handler pairs
     *
     * @return the map of topic, handler pairs
     */
    protected HashMap<Topic, MQTTMessageHandler> initHandlers() {
        Method[] methods = this.getClass().getDeclaredMethods();
        HashMap<Topic, MQTTMessageHandler> topicHandler = new HashMap<>();
        for (Method method : methods) {
            MQTTPublishTopic annotation = method.getAnnotation(MQTTPublishTopic.class);
            if (annotation != null) {
                Topic topic = Topic.parse(annotation.value());
                if (topicHandler.containsKey(topic)) {
                    throw new RuntimeException("Duplicate handler for topic " + topic);
                }
                MQTTMessageHandler consumer = new MQTTMessageHandler(message -> {
                    try {
                        method.invoke(this, message);
                    } catch (Exception e) {
                        LOG.warning("Failed to invoke consumer for topic " + topic + ": " + e.getMessage());
                    }
                });
                topicHandler.put(topic, consumer);
            }
        }
        return topicHandler;
    }

    /**
     * Returns the handler for the given topic
     *
     * @param topic the topic to get the handler for
     * @return the handler for the given topic
     */
    public Optional<MQTTMessageHandler> getHandler(Topic topic) {
        MQTTMessageHandler matchedConsumer = null;
        for (Map.Entry<Topic, MQTTMessageHandler> entry : getHandlers().entrySet()) {
            String topicPattern = String.valueOf(entry.getKey());
            MQTTMessageHandler consumer = entry.getValue();

            // Translate MQTT topic patterns with wildcards (+ and #) into regular expressions
            if (topic.toString().matches(topicPattern.replace("+", "[^/]+").replace("#", ".*"))) {
                matchedConsumer = consumer;
                break;
            }
        }
        return Optional.ofNullable(matchedConsumer);
    }

    /**
     * @return the map of topic, handler pairs
     */
    public HashMap<Topic, MQTTMessageHandler> getHandlers() {
        return handlers;
    }

    protected static Map<String, Object> prepareHeaders(String requestRealm, RemotingConnection connection) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SESSION_KEY, getConnectionIDString(connection));
        headers.put(HEADER_CONNECTION_TYPE, ClientEventService.HEADER_CONNECTION_TYPE_MQTT);
        headers.put(REALM_PARAM_NAME, requestRealm);
        return headers;
    }


}
