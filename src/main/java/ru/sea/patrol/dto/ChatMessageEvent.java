package ru.sea.patrol.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEvent {

    private String username;
    private String content;

    @Override
    public String toString() {
        return "{\"username\":\"" + username + "\",\"content\":\"" + content + "\"}";
    }
}
