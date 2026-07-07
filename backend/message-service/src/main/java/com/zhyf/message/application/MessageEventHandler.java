package com.zhyf.message.application;

public interface MessageEventHandler {

    boolean supports(String eventType);

    void handle(MessageEvent event);
}
