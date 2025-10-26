package ru.sea.patrol.dto.chat;

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
public class GameMessageInput {
  private MessageType type;
  private JsonNode payload;
}