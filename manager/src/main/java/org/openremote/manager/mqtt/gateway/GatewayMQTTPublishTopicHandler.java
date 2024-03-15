package org.openremote.manager.mqtt.gateway;

import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.validation.ConstraintViolationException;
import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.mqtt.MQTTBrokerService;
import org.openremote.manager.mqtt.Topic;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.mqtt.ErrorResponseMessage;
import org.openremote.model.mqtt.SuccessResponseMessage;
import org.openremote.model.util.ValueUtil;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.openremote.manager.event.ClientEventService.CLIENT_INBOUND_QUEUE;
import static org.openremote.manager.mqtt.MQTTHandler.topicTokenIndexToString;
import static org.openremote.manager.mqtt.UserAssetProvisioningMQTTHandler.UNIQUE_ID_PLACEHOLDER;
import static org.openremote.manager.mqtt.gateway.GatewayMQTTHandler.*;
import static org.openremote.model.Constants.*;


// TODO: General todo, authorization caching, and other optimizations, needs to be on topic level
@SuppressWarnings("unused")
public class GatewayMQTTPublishTopicHandler extends MQTTPublishTopicHandler {
    private static final Logger LOG = Logger.getLogger(GatewayMQTTPublishTopicHandler.class.getName());
    private final MQTTBrokerService mqttBrokerService;
    private final AssetStorageService assetStorageService;
    private final ClientEventService clientEventService;
    private final MessageBrokerService messageBrokerService;

    public static final String RESPONSE_TOPIC = "response";

    // Authorization is handled on a topic level, each topic handler should check if the connection is authorized to perform the action
    public GatewayMQTTPublishTopicHandler(MQTTBrokerService mqttBrokerService, AssetStorageService assetStorageService, ClientEventService clientEventService, MessageBrokerService messageBrokerService) {
        this.mqttBrokerService = mqttBrokerService;
        this.assetStorageService = assetStorageService;
        this.clientEventService = clientEventService;
        this.messageBrokerService = messageBrokerService;
    }


    // Asset create request
    @MQTTPublishTopic("+/+/operations/assets/+/create")
    protected void assetCreateRequest(MQTTMessage message) {
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

        String payloadContent = message.getBody().toString(StandardCharsets.UTF_8);
        String realm = topicTokenIndexToString(message.getTopic(), REALM_TOKEN_INDEX);

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

    @MQTTPublishTopic("+/+/operations/assets/+/delete")
    protected void assetDeleteRequest(MQTTMessage message) {
        //TODO: Implement asset delete request
    }


    // Single line attribute update request
    @MQTTPublishTopic("+/+/operations/assets/+/attributes/+/update")
    protected void singleLineAttributeUpdateRequest(MQTTMessage message) {
        String realm = topicTokenIndexToString(message.getTopic(), REALM_TOKEN_INDEX);
        String assetId = topicTokenIndexToString(message.getTopic(), ASSET_ID_TOKEN_INDEX);
        String attributeName = topicTokenIndexToString(message.getTopic(), ATTRIBUTE_NAME_TOKEN_INDEX);
        String payloadContent = message.getBody().toString(StandardCharsets.UTF_8);

        if (!Pattern.matches(ASSET_ID_REGEXP, assetId)) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.ASSET_ID_INVALID);
            LOG.info("Received invalid asset ID " + assetId + " in single-line attribute update request " + message.getConnection().getClientID());
            return;
        }

        AttributeEvent attributeEvent = new AttributeEvent(assetId, attributeName, payloadContent);

        // Checks whether the attributeEvent can be sent by the user
        if (!clientEventService.authorizeEventWrite(realm, message.getAuthContext(), attributeEvent)) {
            publishErrorResponse(message.getTopic(), ErrorResponseMessage.Error.FORBIDDEN);
            return;
        }
        var headers = prepareHeaders(realm, message.getConnection());


        messageBrokerService.getFluentProducerTemplate()
                .withHeaders(headers)
                .withBody(attributeEvent)
                .to(CLIENT_INBOUND_QUEUE)
                .asyncRequest();


        mqttBrokerService.publishMessage(getResponseTopic(message.getTopic()), new SuccessResponseMessage(SuccessResponseMessage.Success.UPDATED, realm), MqttQoS.AT_MOST_ONCE);
    }

    // Multi line attribute update request
    @MQTTPublishTopic("+/+/operations/assets/+/attributes/update")
    protected void multiLineAttributeUpdateRequest(MQTTMessage message) {
        //TODO: Implement multi-line attribute update (expects attribute name and value pairs in the payload)
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
