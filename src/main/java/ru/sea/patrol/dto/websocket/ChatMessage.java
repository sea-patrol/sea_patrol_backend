package ru.sea.patrol.dto.websocket;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ChatMessage {

  private String from;
  private String to;
  private String text;
}
