package ru.sea.patrol.ws.protocol.dto;

import java.util.List;

public record InitGameStateMessage(
        String room,
        WindInfo wind,
        List<PlayerInfo> players) {
}
