package ru.sea.patrol.dto.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sea.patrol.MessageType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageInput {
  private MessageType type;
  private JsonNode payload;
}