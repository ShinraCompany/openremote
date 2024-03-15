package org.openremote.manager.mqtt.gateway;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.openremote.container.security.AuthContext;
import org.openremote.manager.mqtt.Topic;

public class MQTTMessage {
    private final RemotingConnection connection;
    private final Topic topic;
    private final ByteBuf body;
    private final AuthContext authContext;

    public MQTTMessage(RemotingConnection connection, Topic topic, ByteBuf body, AuthContext authContext) {
        this.connection = connection;
        this.topic = topic;
        this.body = body;
        this.authContext = authContext;
    }

    public RemotingConnection getConnection() {
        return connection;
    }

    public Topic getTopic() {
        return topic;
    }

    public ByteBuf getBody() {
        return body;
    }

    public AuthContext getAuthContext() {
        return authContext;
    }
}

