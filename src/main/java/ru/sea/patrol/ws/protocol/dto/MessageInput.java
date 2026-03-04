package ru.sea.patrol.ws.protocol.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sea.patrol.ws.protocol.MessageType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageInput {
  private MessageType type;
  private JsonNode payload;
}
