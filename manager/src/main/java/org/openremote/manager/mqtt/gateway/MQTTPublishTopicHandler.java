package org.openremote.manager.mqtt.gateway;

import org.openremote.manager.mqtt.Topic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;


abstract class MQTTPublishTopicHandler {

    private static final Logger LOG = Logger.getLogger(MQTTPublishTopicHandler.class.getName());
    private final HashMap<Topic, MQTTMessageHandler> handlers;

    MQTTPublishTopicHandler() {
        LOG.info("Initializing MQTT Publish Topic Handler");
        this.handlers = initHandlers();
    }

    /**
     * Scans for all the class methods annotated with {@link MQTTPublishTopic} and creates a map for the topic, handler pairs
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

    // returns the handler for the given topic, if any
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

    public HashMap<Topic, MQTTMessageHandler> getHandlers() {
        return handlers;
    }
}
