package org.openremote.manager.mqtt.gateway;

import java.util.function.Consumer;

public class MQTTMessageHandler {

    private final Consumer<MQTTMessage> handler;

    public MQTTMessageHandler(Consumer<MQTTMessage> consumer) {
        this.handler = consumer;
    }

    public Consumer<MQTTMessage> getHandler() {
        return handler;
    }
}



