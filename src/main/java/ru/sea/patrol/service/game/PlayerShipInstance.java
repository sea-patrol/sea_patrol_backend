package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import ru.sea.patrol.dto.websocket.PlayerInputMessage;

public class PlayerShipInstance {

  private final Body body;
  private static final float MAX_SPEED = 4000f;
  private static final float TURN_SPEED = 3000f;

  private PlayerInputMessage input;

  public PlayerShipInstance(World world, Player player) {
    BodyDef def = new BodyDef();
    def.type = BodyDef.BodyType.DynamicBody;
    def.position.set(player.getX(), player.getZ());
    def.angle = player.getAngle();
    body = world.createBody(def);

    PolygonShape shape = new PolygonShape();
    shape.setAsBox(player.getWidth() / 2, player.getLength() / 2); // ширина=16, длина=30
    FixtureDef sensorDef = new FixtureDef();
    sensorDef.shape = shape;
    sensorDef.density = 1f;
    sensorDef.restitution = 0.3f;
    sensorDef.friction = 0.0f;
    sensorDef.isSensor = false; // ← Сенсор!
    body.createFixture(sensorDef);
    shape.dispose();

    body.setFixedRotation(false);
    body.setAngularDamping(0.5f);
    body.setLinearDamping(1f);

    input = new PlayerInputMessage(false, false, false, false);
  }

  public void update(float delta, Wind wind) {
    if (input == null) {
      return;
    }

    float thrust = 0;

    if (input.up()) {
      thrust += 1;
    }
    if (input.down()) thrust -= 0.5f;

    // Простая модель: сила пропорциональна cos(угол между ветром и парусом)
    float shipAngle = body.getAngle();
    Vector2 force = new Vector2((float) Math.sin(shipAngle), -(float) Math.cos(shipAngle))
            .scl(thrust * MAX_SPEED);

    body.applyForceToCenter(force, true);

    // Поворот
    if (input.left()) body.applyTorque(-TURN_SPEED, true);
    if (input.right()) body.applyTorque(TURN_SPEED, true);
  }

  public Vector2 getPosition() {
    return body.getPosition();
  }

  public float getOrientation() {
    return body.getAngle();
  }

  public float getAngularVelocity() {
    return body.getAngularVelocity();
  }

  public float getVelocity() {return body.getLinearVelocity().len();}

  public float getAngle() {
    return body.getAngle();
  }

  public void dispose() {
    body.getWorld().destroyBody(body);
  }

  public void setInput(PlayerInputMessage input) {
    this.input = input;
  }
}
