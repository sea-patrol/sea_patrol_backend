package ru.sea.patrol.service.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import ru.sea.patrol.ws.protocol.dto.PlayerInputMessage;

public class PlayerShipInstance {

  private static final PlayerInputMessage EMPTY_INPUT = new PlayerInputMessage(false, false, false, false);
  private static final int MIN_SAIL_LEVEL = 0;
  private static final int MAX_SAIL_LEVEL = 3;
  private static final float[] SAIL_LEVEL_FACTORS = {0.0f, 0.35f, 0.7f, 1.0f};
  private final Body body;
  private final Player player;
  private static final float MAX_FORCE_RATIO = 30f;
  private static final float TURN_FORCE_RATIO = 2f;
  private static final float MIN_SAIL_EFFICIENCY = 0.2f;
  private static final float BEAM_REACH_WEIGHT = 0.9f;
  private static final float TAILWIND_WEIGHT = 0.55f;
  private static final float HEADWIND_PENALTY = 0.65f;
  private static final float WIND_REFERENCE_SPEED = 10f;
  private static final float MIN_WIND_SPEED_FACTOR = 0.25f;
  private static final float MAX_WIND_SPEED_FACTOR = 2.0f;

  private PlayerInputMessage input;
  private PlayerInputMessage previousInput;
  private int sailLevel;
  private boolean frozen;

  private float force = 0f;

  public PlayerShipInstance(World world, Player player) {
    this.player = player;
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

    input = EMPTY_INPUT;
    previousInput = EMPTY_INPUT;
    sailLevel = clampSailLevel(player.getSailLevel());
    frozen = false;
    this.player.setSailLevel(sailLevel);
    this.force = player.getWidth() * player.getLength() * MAX_FORCE_RATIO;
  }

  public void update(float delta, Wind wind) {
    if (frozen) {
      return;
    }
    PlayerInputMessage currentInput = input == null ? EMPTY_INPUT : input;
    float shipAngle = body.getAngle();
    float thrust = sailThrustFactor(sailLevel) * calculateSailDriveMultiplier(shipAngle, wind);

    Vector2 forceVector = new Vector2((float) Math.cos(shipAngle), (float) Math.sin(shipAngle))
            .scl(thrust * this.force);

    body.applyForceToCenter(forceVector, true);

    if (isPressed(currentInput.left())) body.applyTorque(TURN_FORCE_RATIO * force, true);
    if (isPressed(currentInput.right())) body.applyTorque(-TURN_FORCE_RATIO * force, true);
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
    PlayerInputMessage normalizedInput = input == null ? EMPTY_INPUT : input;
    if (isPressed(normalizedInput.up()) && !isPressed(previousInput.up())) {
      sailLevel = clampSailLevel(sailLevel + 1);
    }
    if (isPressed(normalizedInput.down()) && !isPressed(previousInput.down())) {
      sailLevel = clampSailLevel(sailLevel - 1);
    }
    player.setSailLevel(sailLevel);
    previousInput = normalizedInput;
    this.input = normalizedInput;
    this.frozen = false;
  }

  public void freeze() {
    input = EMPTY_INPUT;
    previousInput = EMPTY_INPUT;
    frozen = true;
    body.setLinearVelocity(0f, 0f);
    body.setAngularVelocity(0f);
  }

  public int getSailLevel() {
    return sailLevel;
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

  private static float sailThrustFactor(int sailLevel) {
    return SAIL_LEVEL_FACTORS[clampSailLevel(sailLevel)];
  }

  private static int clampSailLevel(int sailLevel) {
    return Math.max(MIN_SAIL_LEVEL, Math.min(MAX_SAIL_LEVEL, sailLevel));
  }

  private static boolean isPressed(Boolean value) {
    return Boolean.TRUE.equals(value);
  }
}
