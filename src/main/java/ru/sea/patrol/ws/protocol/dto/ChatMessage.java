package ru.sea.patrol.ws.protocol.dto;

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
