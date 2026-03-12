package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import ru.sea.patrol.ws.protocol.dto.PlayerInputMessage;

public class PlayerShipInstance {

  private final Body body;
  private static final float MAX_FORCE_RATIO = 30f;
  private static final float TURN_FORCE_RATIO = 2f;
  private static final float MIN_SAIL_EFFICIENCY = 0.2f;
  private static final float BEAM_REACH_WEIGHT = 0.9f;
  private static final float TAILWIND_WEIGHT = 0.55f;
  private static final float HEADWIND_PENALTY = 0.65f;
  private static final float WIND_REFERENCE_SPEED = 10f;
  private static final float MIN_WIND_SPEED_FACTOR = 0.25f;
  private static final float MAX_WIND_SPEED_FACTOR = 2.0f;
  private static final float REVERSE_THRUST_FACTOR = 0.35f;

  private PlayerInputMessage input;

  private float force = 0f;

  public PlayerShipInstance(World world, Player player) {
    BodyDef def = new BodyDef();
    def.type = BodyDef.BodyType.DynamicBody;
    def.position.set(player.getZ(), player.getX());
    def.angle = player.getAngle();
    body = world.createBody(def);

    PolygonShape shape = new PolygonShape();
    shape.setAsBox(player.getLength() / 2, player.getWidth() / 2);
    FixtureDef sensorDef = new FixtureDef();
    sensorDef.shape = shape;
    sensorDef.density = 1f;
    sensorDef.restitution = 0.1f;
    sensorDef.friction = 0.0f;
    sensorDef.isSensor = false;
    body.createFixture(sensorDef);
    shape.dispose();

    body.setFixedRotation(false);
    body.setAngularDamping(0.8f);
    body.setLinearDamping(0.8f);

    input = new PlayerInputMessage(false, false, false, false);
    this.force = player.getWidth() * player.getLength() * MAX_FORCE_RATIO;
  }

  public void update(float delta, Wind wind) {
    if (input == null) {
      return;
    }

    float thrust = 0;
    float shipAngle = body.getAngle();

    if (input.up()) {
      thrust += calculateSailDriveMultiplier(shipAngle, wind);
    }
    if (input.down()) {
      thrust -= REVERSE_THRUST_FACTOR;
    }

    Vector2 forceVector = new Vector2((float) Math.cos(shipAngle), (float) Math.sin(shipAngle))
            .scl(thrust * this.force);

    body.applyForceToCenter(forceVector, true);

    if (input.left()) body.applyTorque(TURN_FORCE_RATIO * force, true);
    if (input.right()) body.applyTorque(-TURN_FORCE_RATIO * force, true);
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

  public float getVelocity() {
    return body.getLinearVelocity().len();
  }

  public float getAngle() {
    return body.getAngle();
  }

  public void setFrontendTransform(float x, float z, float angle) {
    body.setTransform(z, x, angle);
    body.setLinearVelocity(0f, 0f);
    body.setAngularVelocity(0f);
  }

  public void dispose() {
    body.getWorld().destroyBody(body);
  }

  public void setInput(PlayerInputMessage input) {
    this.input = input;
  }

  public void freeze() {
    input = new PlayerInputMessage(false, false, false, false);
    body.setLinearVelocity(0f, 0f);
    body.setAngularVelocity(0f);
  }

  public float getFrontendX() {
    return body.getPosition().y;
  }

  public float getFrontendZ() {
    return body.getPosition().x;
  }

  private static float calculateSailDriveMultiplier(float shipAngle, Wind wind) {
    if (wind == null || wind.getDirection() == null) {
      return MIN_SAIL_EFFICIENCY;
    }

    Vector2 shipForward = new Vector2((float) Math.cos(shipAngle), (float) Math.sin(shipAngle));
    float alignment = shipForward.dot(wind.getDirection());
    float beamReachFactor = 1f - Math.abs(alignment);
    float tailwindFactor = Math.max(0f, alignment);
    float headwindFactor = Math.max(0f, -alignment);

    float sailEfficiency = clamp(
        MIN_SAIL_EFFICIENCY
            + beamReachFactor * BEAM_REACH_WEIGHT
            + tailwindFactor * TAILWIND_WEIGHT
            - headwindFactor * HEADWIND_PENALTY,
        0.1f,
        1.5f
    );

    float windSpeedFactor = clamp(
        wind.getSpeed() / WIND_REFERENCE_SPEED,
        MIN_WIND_SPEED_FACTOR,
        MAX_WIND_SPEED_FACTOR
    );

    return sailEfficiency * windSpeedFactor;
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
