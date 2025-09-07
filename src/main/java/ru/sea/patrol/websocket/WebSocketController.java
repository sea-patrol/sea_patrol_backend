package ru.sea.patrol.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class WebSocketController {

    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public List<ChatMessageDto> getChat(String s) { return WebSocketDataFactory.getChat(); }

}
