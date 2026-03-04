package ru.sea.patrol.ws.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sea.patrol.ws.protocol.MessageType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageOutput {
  private MessageType type;
  private Object payload;
}
