package ru.sea.patrol.websocket;

import java.util.Arrays;
import java.util.List;

public class WebSocketDataFactory {

    public static List<ChatMessageDto> getChat() {
        return Arrays.asList(
                new ChatMessageDto("Ivan", "Hello! My name is Ivan!"),
                new ChatMessageDto("Danil", "Hello! My name is Danil!"),
                new ChatMessageDto("Иван", "Привет! Меня зовут Иван!"),
                new ChatMessageDto("Данил", "Привет! Меня зовут Данил!")
        );
    }

}
