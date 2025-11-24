package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import lombok.Getter;

import java.util.Random;

public class Wind {

  @Getter
  private final Vector2 direction = new Vector2(1, 0);

  @Getter
  private float speed = 10f;

  private final Random rand = new Random();

  public void update(float delta) {
    float change = (rand.nextFloat() - 0.5f) * 0.5f * delta;
    float angle = direction.angleRad() + change;
    direction.set((float) Math.cos(angle), (float) Math.sin(angle));
  }
}
