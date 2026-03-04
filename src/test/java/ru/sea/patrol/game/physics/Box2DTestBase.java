package ru.sea.patrol.game.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Tag("physics")
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class Box2DTestBase {

	protected static final float EPS = 1.0e-3f;

	protected World world;

	@BeforeAll
	void initBox2D() {
		Box2D.init();
	}

	@BeforeEach
	void createWorld() {
		world = new World(new Vector2(0, 0), true);
	}

	@AfterEach
	void disposeWorld() {
		if (world != null) {
			world.dispose();
			world = null;
		}
	}

	protected static void step(World world, float timeStep, int steps) {
		for (int i = 0; i < steps; i++) {
			world.step(timeStep, 6, 2);
		}
	}
}

