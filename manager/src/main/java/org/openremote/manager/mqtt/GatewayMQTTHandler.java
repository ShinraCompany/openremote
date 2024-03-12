/*
 * Copyright 2021, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.mqtt;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.keycloak.KeycloakSecurityContext;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.security.ManagerKeycloakIdentityProvider;
import org.openremote.model.Container;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.asset.impl.GatewayAsset;
import org.openremote.model.asset.impl.GatewayV2Asset;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.openremote.manager.mqtt.Topic.SINGLE_LEVEL_TOKEN;
import static org.openremote.model.Constants.ASSET_ID_REGEXP;
import static org.openremote.model.syslog.SyslogCategory.API;

public class GatewayMQTTHandler extends MQTTHandler {

    // main topics
    public static final String EVENTS_TOPIC = "events"; // prefix for event topics, users subscribe to these
    public static final String OPERATIONS_TOPIC = "operations"; // prefix for operation topics, users publish to these (or subscribe to responses)

    // sub-topics
    public static final String PROVISION_TOPIC = "provision"; // gateway provisioning topic
    public static final String ASSETS_TOPIC = "assets";
    public static final String ATTRIBUTES_TOPIC = "attributes";

    // operation topics
    public static final String HEALTH_TOPIC = "health";
    public static final String CREATE_TOPIC = "create";
    public static final String READ_TOPIC = "read";
    public static final String UPDATE_TOPIC = "update";
    public static final String DELETE_TOPIC = "delete";
    public static final String RESPONSE_TOPIC = "response"; // response suffix, responses will be published to this topic

    // token indexes
    public static final int OPERATIONS_PREFIX_TOKEN_INDEX = 2;
    public static final int EVENTS_PREFIX_TOKEN_INDEX = 2;
    public static final int ATTRIBUTE_NAME_TOKEN_INDEX = 6;
    public static final int ASSET_ID_TOKEN_INDEX = 4;
    public static final int REQUEST_RESPONSE_IDENTIFIER_TOKEN_INDEX = 4;
    public static final int CLIENT_ID_TOKEN_INDEX = 1;
    public static final int REALM_TOKEN_INDEX = 0;

    protected static final Logger LOG = SyslogCategory.getLogger(API, GatewayMQTTHandler.class);
    protected AssetProcessingService assetProcessingService;
    protected TimerService timerService;
    protected AssetStorageService assetStorageService;
    protected ManagerKeycloakIdentityProvider identityProvider;
    protected boolean isKeycloak;

    // topic - handler map
    protected HashMap<String, Consumer<PublishTopicMessage>> topicHandlers = new HashMap<>();

    @Override
    public void start(Container container) throws Exception {
        super.start(container);
        timerService = container.getService(TimerService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        ManagerIdentityService identityService = container.getService(ManagerIdentityService.class);

        if (!identityService.isKeycloakEnabled()) {
            LOG.warning("MQTT connections are not supported when not using Keycloak identity provider");
            isKeycloak = false;
        } else {
            isKeycloak = true;
            identityProvider = (ManagerKeycloakIdentityProvider) identityService.getIdentityProvider();
        }
    }

    @Override
    public void init(Container container) throws Exception {
        super.init(container);
        registerPublishTopicHandlers();
    }

    @Override
    public void onConnect(RemotingConnection connection) {
        super.onConnect(connection);
        LOG.fine("New MQTT connection session " + MQTTBrokerService.connectionToString(connection));

        if (isGatewayConnection(connection)) {
            updateLinkedGatewayStatus(connection, ConnectionStatus.CONNECTED);
        }

    }

    @Override
    public boolean handlesTopic(Topic topic) {
        return topicMatches(topic);
    }

    @Override
    public boolean topicMatches(Topic topic) {
        if (isEventsTopic(topic)) {
            return true;
        }
        return isOperationsTopic(topic);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    public boolean canSubscribe(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        if (!isKeycloak) {
            LOG.fine("Identity provider is not keycloak");
            return false;
        }

        // only events topics are allowed for subscriptions (unless its the operations topic with a response topic)
        if (!isEventsTopic(topic)) {
            if (!isOperationsTopic(topic) && !isResponseTopic(topic)) {
                LOG.finest("Invalid topic " + topic + " for subscriptions");
                return false;
            }
        }

        //TODO: Authorization and implement
        return true;
    }

    @Override
    public void onSubscribe(RemotingConnection connection, Topic topic) {
        // TODO:: Implement
    }

    @Override
    public void onUnsubscribe(RemotingConnection connection, Topic topic) {
        // TODO: Implement
    }

    @Override
    public Set<String> getPublishListenerTopics() {
        return topicHandlers.keySet();
    }

    protected void registerPublishTopicHandlers() {
        // topic: realm/clientId/operations/assets/+/attributes/+/update
        topicHandlers.put(SINGLE_LEVEL_TOKEN + "/" + SINGLE_LEVEL_TOKEN + "/" + OPERATIONS_TOPIC + "/" + ASSETS_TOPIC + "/" + SINGLE_LEVEL_TOKEN + "/" + ATTRIBUTES_TOPIC + "/" + SINGLE_LEVEL_TOKEN + "/" + UPDATE_TOPIC,
                this::handleSingleLineAttributeUpdateRequest);
        // topic: realm/clientId/operations/assets/+/attributes/update
        topicHandlers.put(SINGLE_LEVEL_TOKEN + "/" + SINGLE_LEVEL_TOKEN + "/" + OPERATIONS_TOPIC + "/" + ASSETS_TOPIC + "/" + SINGLE_LEVEL_TOKEN + "/" + ATTRIBUTES_TOPIC + "/" + UPDATE_TOPIC,
                this::handleMultiLineAttributeUpdateRequest);

        // topic: realm/clientId/operations/assets/+/create
        topicHandlers.put(SINGLE_LEVEL_TOKEN + "/" + SINGLE_LEVEL_TOKEN + "/" + OPERATIONS_TOPIC + "/" + ASSETS_TOPIC + "/" + SINGLE_LEVEL_TOKEN + "/" + CREATE_TOPIC,
                this::handleCreateAssetRequest);

        topicHandlers.forEach((topic, handler) -> {
            LOG.fine("Registered handler for topic " + topic);
        });
    }


    @Override
    public boolean canPublish(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        if (!isKeycloak) {
            LOG.fine("Identity provider is not keycloak");
            return false;
        }

        if (!isOperationsTopic(topic)) {
            LOG.finest("Invalid topic " + topic + " for publishing" + OPERATIONS_TOPIC);
            return false;
        }

        //TODO: Authorization and implement

        if (getHandlerFromTopic(topic).isEmpty()) {
            LOG.fine("No handler found for topic " + topic);
            return false;
        }

        return true;
    }

    @Override
    public void onPublish(RemotingConnection connection, Topic topic, ByteBuf body) {
        if (!isGatewayConnection(connection)) {
            LOG.info("Received message from non-gateway connection " + MQTTBrokerService.connectionToString(connection));
            return;
        }

        // TODO: Authorization
        var handler = getHandlerFromTopic(topic);
        if (handler.isPresent()) {
            handler.get().accept(new PublishTopicMessage(connection, topic, body));
        } else {
            LOG.fine("No handler found for topic " + topic);
        }
    }

    @Override
    public void onConnectionLost(RemotingConnection connection) {
        if (isGatewayConnection(connection)) {
            updateLinkedGatewayStatus(connection, ConnectionStatus.DISCONNECTED);
        }
    }

    @Override
    public void onDisconnect(RemotingConnection connection) {
        if (isGatewayConnection(connection)) {
            updateLinkedGatewayStatus(connection, ConnectionStatus.DISCONNECTED);
        }

    }

    private void handleCreateAssetRequest(PublishTopicMessage publishTopicMessage) {
        String responseIdentifier = topicTokenIndexToString(publishTopicMessage.topic, REQUEST_RESPONSE_IDENTIFIER_TOKEN_INDEX);
        String payloadContent = publishTopicMessage.body.toString(StandardCharsets.UTF_8);

        if (responseIdentifier == null) {
            LOG.info("Received create asset request " + publishTopicMessage.connection.getClientID() + " with missing response identifier");
            return;
        }

        LOG.fine("Received create asset request " + publishTopicMessage.connection.getClientID() + " with response identifier " + responseIdentifier + " with payload " + payloadContent);
    }


    protected void handleSingleLineAttributeUpdateRequest(PublishTopicMessage message) {
        String assetId = topicTokenIndexToString(message.topic, ASSET_ID_TOKEN_INDEX);
        String attributeName = topicTokenIndexToString(message.topic, ATTRIBUTE_NAME_TOKEN_INDEX);
        String payloadContent = message.body.toString(StandardCharsets.UTF_8);

        if (!Pattern.matches(ASSET_ID_REGEXP, assetId)) {
            LOG.info("Received invalid asset ID " + assetId + " in single-line attribute update request " + message.connection.getClientID());
            return;
        }

        LOG.finer("Received single-line attribute update request " + message.connection.getClientID() + " for asset " + assetId + " and attribute " + attributeName + " with payload " + payloadContent);
    }

    protected void handleMultiLineAttributeUpdateRequest(PublishTopicMessage message) {
        String assetId = topicTokenIndexToString(message.topic, ASSET_ID_TOKEN_INDEX);
        String attributeName = topicTokenIndexToString(message.topic, ATTRIBUTE_NAME_TOKEN_INDEX);
        String payloadContent = message.body.toString(StandardCharsets.UTF_8);

        if (!Pattern.matches(ASSET_ID_REGEXP, assetId)) {
            LOG.info("Received invalid asset ID " + assetId + " in multi-line attribute update request " + message.connection.getClientID());
            return;
        }
        LOG.fine("Received multi-line attribute update request " + message.connection.getClientID() + " for asset " + assetId + " and attribute " + attributeName + " with payload " + payloadContent);
    }

    protected void sendAttributeEvent(AttributeEvent event) {
        assetProcessingService.sendAttributeEvent(event, GatewayMQTTHandler.class.getName());
    }

    protected void updateLinkedGatewayStatus(RemotingConnection connection, ConnectionStatus status) {
        GatewayV2Asset gatewayAsset = (GatewayV2Asset) assetStorageService.find(new AssetQuery()
                .types(GatewayV2Asset.class).attributeValue(GatewayV2Asset.CLIENT_ID.getName(), connection.getClientID()));

        if (gatewayAsset != null) {
            LOG.fine("Linked Gateway asset found for MQTT client, updating connection status to " + status);
            sendAttributeEvent(new AttributeEvent(gatewayAsset.getId(), GatewayAsset.STATUS, status));
        }
    }

    protected boolean isOperationsTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, OPERATIONS_PREFIX_TOKEN_INDEX), OPERATIONS_TOPIC);
    }

    protected boolean isResponseTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, topic.getTokens().size() - 1), RESPONSE_TOPIC);
    }

    protected boolean isEventsTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, EVENTS_PREFIX_TOKEN_INDEX), EVENTS_TOPIC);
    }

    protected boolean isGatewayConnection(RemotingConnection connection) {
        return assetStorageService.find(new AssetQuery().types(GatewayV2Asset.class).attributeValue(GatewayV2Asset.CLIENT_ID.getName(), connection.getClientID())) != null;
    }

    protected Optional<Consumer<PublishTopicMessage>> getHandlerFromTopic(Topic topic) {
        Consumer<PublishTopicMessage> matchedHandler = null;
        for (Map.Entry<String, Consumer<PublishTopicMessage>> entry : topicHandlers.entrySet()) {
            String topicPattern = entry.getKey();
            Consumer<PublishTopicMessage> handler = entry.getValue();

            // Translate MQTT topic patterns with wildcards (+ and #) into regular expressions
            if (topic.toString().matches(topicPattern.replace("+", "[^/]+").replace("#", ".*"))) {
                matchedHandler = handler;
                break;
            }
        }
        return Optional.ofNullable(matchedHandler);
    }

    protected record PublishTopicMessage(RemotingConnection connection, Topic topic, ByteBuf body) {
    }


}

