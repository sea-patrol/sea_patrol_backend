package ru.sea.patrol.service.game;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import ru.sea.patrol.dto.websocket.MessageOutput;

@Getter
@Setter
@Accessors(chain = true)
public class Player {

  private int health;
  private int maxHealth;
  private float velocity;
  private float x;
  private float z;
  private float angle;
  private String model;
  private float height;
  private float width;
  private float length;

  private GameRoom room;
  private Disposable roomSubscription;

  private final String name;
  private final Sinks.Many<MessageOutput> sink;

  public Player(String name) {
    this.name = name;
    this.sink = Sinks.many().unicast().onBackpressureBuffer();
  }

  public void reply(MessageOutput message) {
    if (message != null) {
      sink.tryEmitNext(message);
    }
  }

  public void joinRoom(GameRoom room) {
    if (this.room != null) {
      this.room.leave(this.name);
    }
    this.room = room;
    roomSubscription = room.getSink().asFlux().subscribe(this::reply);
  }

  public void leaveRoom() {
    this.room = null;
    if (this.roomSubscription != null) {
      this.roomSubscription.dispose();
      this.roomSubscription = null;
    }
  }
}
