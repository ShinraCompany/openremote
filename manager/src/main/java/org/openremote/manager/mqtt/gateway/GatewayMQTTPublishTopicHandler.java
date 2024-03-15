package org.openremote.manager.mqtt.gateway;

import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.validation.ConstraintViolationException;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.mqtt.MQTTBrokerService;
import org.openremote.manager.mqtt.Topic;
import org.openremote.model.asset.Asset;
import org.openremote.model.mqtt.ErrorResponseMessage;
import org.openremote.model.mqtt.SuccessResponseMessage;
import org.openremote.model.util.ValueUtil;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.openremote.manager.mqtt.MQTTHandler.getAuthContextFromConnection;
import static org.openremote.manager.mqtt.MQTTHandler.topicTokenIndexToString;
import static org.openremote.manager.mqtt.UserAssetProvisioningMQTTHandler.UNIQUE_ID_PLACEHOLDER;
import static org.openremote.manager.mqtt.gateway.GatewayMQTTHandler.*;
import static org.openremote.model.Constants.*;

public class GatewayMQTTPublishTopicHandler {
    private static final Logger LOG = Logger.getLogger(GatewayMQTTPublishTopicHandler.class.getName());
    private final MQTTBrokerService mqttBrokerService;
    private final AssetStorageService assetStorageService;
    public static final String RESPONSE_TOPIC = "response";

    private final HashMap<Topic, MQTTMessageHandler> handlers;

    public GatewayMQTTPublishTopicHandler(MQTTBrokerService mqttBrokerService, AssetStorageService assetStorageService) {
        this.mqttBrokerService = mqttBrokerService;
        this.assetStorageService = assetStorageService;
        this.handlers = initHandlers();
    }

    public HashMap<Topic, MQTTMessageHandler> getHandlers() {
        return handlers;
    }


    @MQTTPublishTopic("+/+/operations/assets/+/create")
    private void assetCreateRequest(MQTTMessage message) {
        String payloadContent = message.getBody().toString(StandardCharsets.UTF_8);
        String realm = topicTokenIndexToString(message.getTopic(), REALM_TOKEN_INDEX);

        // restricted users are not allowed to write assets
        if (message.getAuthContext().hasRealmRole(RESTRICTED_USER_REALM_ROLE)) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.FORBIDDEN);
            return;
        }

        // only users with the write assets role are allowed to create assets
        if (!message.getAuthContext().hasResourceRole(WRITE_ASSETS_ROLE, KEYCLOAK_CLIENT_ID)) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.FORBIDDEN);
            return;
        }

        // Replace any placeholders in the template
        String assetTemplate = payloadContent;
        assetTemplate = assetTemplate.replaceAll(UNIQUE_ID_PLACEHOLDER, UniqueIdentifierGenerator.generateId());

        var optionalAsset = ValueUtil.parse(assetTemplate, Asset.class);
        if (optionalAsset.isEmpty()) {
            LOG.fine("Invalid asset template " + payloadContent + " in create asset request " + message.getConnection().getClientID());
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.MESSAGE_INVALID);
            return;
        }

        Asset<?> asset = optionalAsset.get();
        asset.setId(UniqueIdentifierGenerator.generateId());
        asset.setRealm(realm);

        try {
            assetStorageService.merge(asset);
        } catch (ConstraintViolationException e) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.MESSAGE_INVALID, e.getMessage());
            LOG.fine("Failed to create asset " + asset + " in realm " + realm + " " + message.getConnection().getClientID());
            return;
        } catch (Exception e) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.SERVER_ERROR);
            LOG.warning("Failed to create asset " + asset + " in realm " + realm + " " + message.getConnection().getClientID());
            return;
        }

        mqttBrokerService.publishMessage(getResponseTopic(message.getTopic()),
                new SuccessResponseMessage(SuccessResponseMessage.Success.CREATED, realm, asset), MqttQoS.AT_MOST_ONCE
        );
    }


    @MQTTPublishTopic("+/+/operations/assets/+/attributes/+/update")
    protected void singleLineAttributeUpdateRequest(MQTTMessage message) {
        String realm = topicTokenIndexToString(message.getTopic(), REALM_TOKEN_INDEX);
        String assetId = topicTokenIndexToString(message.getTopic(), ASSET_ID_TOKEN_INDEX);
        String attributeName = topicTokenIndexToString(message.getTopic(), ATTRIBUTE_NAME_TOKEN_INDEX);
        String payloadContent = message.getBody().toString(StandardCharsets.UTF_8);

        if (!Pattern.matches(ASSET_ID_REGEXP, assetId)) {
            LOG.info("Received invalid asset ID " + assetId + " in single-line attribute update request " + message.getConnection().getClientID());
            return;
        }
    }

    @MQTTPublishTopic("+/+/operations/assets/+/attributes/update")
    protected void multiLineAttributeUpdateRequest(MQTTMessage message) {
        String realm = topicTokenIndexToString(message.getTopic(), REALM_TOKEN_INDEX);
        String assetId = topicTokenIndexToString(message.getTopic(), ASSET_ID_TOKEN_INDEX);
        String attributeName = topicTokenIndexToString(message.getTopic(), ATTRIBUTE_NAME_TOKEN_INDEX);
        String payloadContent = message.getBody().toString(StandardCharsets.UTF_8);

        var authContext = getAuthContextFromConnection(message.getConnection());

        if (!Pattern.matches(ASSET_ID_REGEXP, assetId)) {
            LOG.info("Received invalid asset ID " + assetId + " in multi-line attribute update request " + message.getConnection().getClientID());
            return;
        }

    }

    // Initialize the topic consumers by scanning for methods annotated with @MQTTPublishTopic
    protected HashMap<Topic, MQTTMessageHandler> initHandlers() {
        Method[] methods = GatewayMQTTPublishTopicHandler.class.getDeclaredMethods();
        HashMap<Topic, MQTTMessageHandler> topicConsumers = new HashMap<>();
        for (Method method : methods) {
            MQTTPublishTopic annotation = method.getAnnotation(MQTTPublishTopic.class);
            if (annotation != null) {
                Topic topic = Topic.parse(annotation.value());
                MQTTMessageHandler consumer = new MQTTMessageHandler(message -> {
                    try {
                        method.invoke(this, message);
                    } catch (Exception e) {
                        LOG.warning("Failed to invoke consumer for topic " + topic + ": " + e.getMessage());
                    }
                });
                topicConsumers.put(topic, consumer);
            }
        }
        return topicConsumers;
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

    protected void publishErrorResponse(Topic topic, ErrorResponseMessage.Error error) {
        mqttBrokerService.publishMessage(getResponseTopic(topic), new ErrorResponseMessage(error), MqttQoS.AT_MOST_ONCE);
    }

    protected void publishErrorResponse(Topic topic, ErrorResponseMessage.Error error, String message) {
        mqttBrokerService.publishMessage(getResponseTopic(topic), new ErrorResponseMessage(error, message), MqttQoS.AT_MOST_ONCE);
    }

    public String getResponseTopic(Topic topic) {
        return topic.toString() + "/" + RESPONSE_TOPIC;
    }

}
