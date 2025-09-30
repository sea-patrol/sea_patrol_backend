package ru.sea.patrol.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import ru.sea.patrol.handler.ChatHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

  @Bean
  public HandlerMapping handlerMapping(ChatHandler chatHandler) {
    Map<String, WebSocketHandler> map = new HashMap<>();
    map.put("/ws/chat", chatHandler);

    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setUrlMap(map);
    mapping.setOrder(1);
    return mapping;
  }

  @Bean
  public WebSocketHandlerAdapter handlerAdapter() {
    return new WebSocketHandlerAdapter();
  }
}
