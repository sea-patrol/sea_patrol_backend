package ru.sea.patrol.websocket;

//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import lombok.Setter;
//
//@AllArgsConstructor
//@Getter
//@Setter
public class ChatMessageDto {

    private String username;
    private String msg;

    public ChatMessageDto(String username, String msg) {
        this.username = username;
        this.msg = msg;
    }

    public String getUsername() {
        return username;
    }

    public String getMsg() {
        return msg;
    }
}

