package ru.sea.patrol.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RoomRegistry {

  private final GameRoomProperties roomProperties;
  private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

  public RoomRegistry(GameRoomProperties roomProperties) {
    this.roomProperties = roomProperties;
  }

  public synchronized GameRoom getOrCreateRoom(String roomName) {
    var existingRoom = rooms.get(roomName);
    if (existingRoom != null) {
      return existingRoom;
    }

    var createdRoom = new GameRoom(roomName, roomProperties.updatePeriod().toMillis());
    rooms.put(roomName, createdRoom);
    return createdRoom;
  }

  public GameRoom findRoom(String roomName) {
    return rooms.get(roomName);
  }

  public synchronized boolean removeRoomIfEmpty(String roomName) {
    var room = rooms.get(roomName);
    if (room == null || !room.isEmpty()) {
      return false;
    }

    rooms.remove(roomName);
    room.stop();
    return true;
  }

  public synchronized int roomCount() {
    return rooms.size();
  }

  public synchronized boolean hasRoom(String roomName) {
    return rooms.containsKey(roomName);
  }

  public synchronized List<GameRoom> roomsSnapshot() {
    return List.copyOf(new ArrayList<>(rooms.values()));
  }
}
