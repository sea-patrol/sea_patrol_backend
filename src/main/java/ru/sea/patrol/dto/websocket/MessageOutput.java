package ru.sea.patrol.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sea.patrol.MessageType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageOutput {
  private MessageType type;
  private Object payload;
}