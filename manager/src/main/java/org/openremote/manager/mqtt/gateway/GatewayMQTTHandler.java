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
package org.openremote.manager.mqtt.gateway;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.keycloak.KeycloakSecurityContext;
import org.openremote.container.security.AuthContext;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.mqtt.MQTTHandler;
import org.openremote.manager.mqtt.Topic;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.security.ManagerKeycloakIdentityProvider;
import org.openremote.model.Container;
import org.openremote.model.asset.impl.GatewayV2Asset;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;

import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.openremote.manager.mqtt.gateway.GatewayMQTTPublishTopicHandler.RESPONSE_TOPIC;
import static org.openremote.model.syslog.SyslogCategory.API;

public class GatewayMQTTHandler extends MQTTHandler {

    // main topics
    public static final String EVENTS_TOPIC = "events"; // prefix for event topics, users subscribe to these
    public static final String OPERATIONS_TOPIC = "operations"; // prefix for operation topics, users publish to these (or subscribe to responses)

    // token indexes
    public static final int REALM_TOKEN_INDEX = 0;
    public static final int CLIENT_ID_TOKEN_INDEX = 1;
    public static final int OPERATIONS_TOKEN_INDEX = 2;
    public static final int EVENTS_TOKEN_INDEX = 2;
    public static final int ASSETS_TOKEN_INDEX = 3;
    public static final int ASSET_ID_TOKEN_INDEX = 4;
    public static final int ATTRIBUTES_TOKEN_INDEX = 5;
    public static final int ATTRIBUTE_NAME_TOKEN_INDEX = 6;

    protected static final Logger LOG = SyslogCategory.getLogger(API, GatewayMQTTHandler.class);
    protected AssetProcessingService assetProcessingService;
    protected TimerService timerService;
    protected AssetStorageService assetStorageService;
    protected ManagerKeycloakIdentityProvider identityProvider;
    protected GatewayMQTTPublishTopicHandler publishTopicHandler;
    protected boolean isKeycloak;

    @Override
    public void start(Container container) throws Exception {
        super.start(container);
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
        timerService = container.getService(TimerService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        publishTopicHandler = new GatewayMQTTPublishTopicHandler(mqttBrokerService, assetStorageService, clientEventService, messageBrokerService);
    }

    @Override
    public void onConnect(RemotingConnection connection) {
        super.onConnect(connection);
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

        // TODO: Proper authorization checks
        if (!isEventsTopic(topic)) {
            // check if the topic is a request-response operation topic
            if (isOperationsTopic(topic) && isResponseTopic(topic)) {
                var topicWithoutResponseSuffix = Topic.parse(topic.toString().replace("/" + RESPONSE_TOPIC, ""));
                if (publishTopicHandler.getHandler(topicWithoutResponseSuffix).isPresent()) {
                    return true;
                }
            }
            LOG.finest("Invalid topic " + topic + " for subscribing" + OPERATIONS_TOPIC);
            return false;
        }

        return true;
    }

    @Override
    public void onSubscribe(RemotingConnection connection, Topic topic) {
        // TODO:: Implement
        LOG.fine("Subscribed to topic " + topic);
    }

    @Override
    public void onUnsubscribe(RemotingConnection connection, Topic topic) {
        // TODO: Implement
        LOG.fine("Unsubscribed from topic " + topic);
    }

    @Override
    public Set<String> getPublishListenerTopics() {
        return publishTopicHandler.getHandlers().keySet().stream().map(Topic::toString).collect(Collectors.toSet());
    }


    @Override
    public boolean canPublish(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        if (!isKeycloak) {
            LOG.fine("Identity provider is not keycloak");
            return false;
        }

        AuthContext authContext = getAuthContextFromSecurityContext(securityContext);
        if (authContext == null) {
            LOG.finer("Anonymous publish not supported: topic=" + topic + ", connection=" + connection);
            return false;
        }

        if (!isOperationsTopic(topic)) {
            LOG.finest("Invalid topic " + topic + " for publishing" + OPERATIONS_TOPIC);
            return false;
        }

        var topicHandler = publishTopicHandler.getHandler(topic);
        if (topicHandler.isEmpty()) {
            LOG.fine("No handler found for topic " + topic);
            return false;
        }

        return true;
    }

    @Override
    public void onPublish(RemotingConnection connection, Topic topic, ByteBuf body) {
        var authContext = getAuthContextFromConnection(connection);
        if (authContext.isEmpty()) {
            LOG.finer("Anonymous publish not supported: topic=" + topic + ", connection=" + connection);
            return;
        }

        var topicHandler = publishTopicHandler.getHandler(topic);
        if (topicHandler.isPresent()) {
            topicHandler.get().getHandler().accept(new MQTTMessage(connection, topic, body, authContext.get()));
        } else {
            LOG.fine("No handler found for topic " + topic);
        }
    }

    @Override
    public void onConnectionLost(RemotingConnection connection) {
    }

    @Override
    public void onDisconnect(RemotingConnection connection) {
    }


    protected void sendAttributeEvent(AttributeEvent event) {
        assetProcessingService.sendAttributeEvent(event, GatewayMQTTHandler.class.getName());
    }


    protected boolean isOperationsTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, OPERATIONS_TOKEN_INDEX), OPERATIONS_TOPIC);
    }

    protected boolean isResponseTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, topic.getTokens().size() - 1), RESPONSE_TOPIC);
    }

    protected boolean isEventsTopic(Topic topic) {
        return Objects.equals(topicTokenIndexToString(topic, EVENTS_TOKEN_INDEX), EVENTS_TOPIC);
    }

    protected boolean isGatewayConnection(RemotingConnection connection) {
        return assetStorageService.find(new AssetQuery().types(GatewayV2Asset.class)
                .attributeValue(GatewayV2Asset.CLIENT_ID.getName(), connection.getClientID())) != null;
    }


}


