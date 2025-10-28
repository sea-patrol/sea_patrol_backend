package ru.sea.patrol.service.game;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class Player {
  private String name;
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
}
