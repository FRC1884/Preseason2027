package org.Griffins1884.frc2027.subsystems.elevator;

import org.Griffins1884.frc2027.commands.ElevatorCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;

import static org.junit.jupiter.api.Assertions.*;

class ElevatorSimulationVerificationTest {

  private static final double POSITION_ASSERT_MARGIN_METERS = 0.025;

  private ElevatorIOSim simulatedIO;
  private ElevatorSubsystem elevatorMechanism;

  @BeforeEach
  void setUp() {
    assertTrue(HAL.initialize(), "HAL failed to initialize for simulation test");

    simulatedIO = new ElevatorIOSim();
    elevatorMechanism = new ElevatorSubsystem(simulatedIO);
  }

  private void cycle(int cycles) {
    for (int stepIndex = 0; stepIndex < cycles; stepIndex++) {
      elevatorMechanism.periodic();
    }
  }

  @Test
  @DisplayName("All ok")
  void ascendsToCommandedTarget() {
    double targetHeight = 0.65;
    elevatorMechanism.setGoalHeight(targetHeight);

    // 100 cycles = 2.0 seconds at 20 ms steps.
    cycle(100);

    assertTrue(elevatorMechanism.isAtTarget(), "Elevator failed to reach");
    assertEquals(
        targetHeight,
        simulatedIO.getPosition(),
        POSITION_ASSERT_MARGIN_METERS,
        "Final elevator position not right");
  }

  @Test
  void doesNotDriveBelowFloor() {
    elevatorMechanism.setGoalHeight(0.0);

    cycle(50);

    assertTrue(
        simulatedIO.getPosition() >= 0.0,
        "Carriage descended below physical floor datum");
  }

  @Test
  void clampsGoalRequests() {
    elevatorMechanism.setGoalHeight(99.0);
    assertEquals(
        ElevatorConstants.MAX_HEIGHT,
        elevatorMechanism.getTargetHeightMeters(),
        1e-9,
        "Over-travel request was not clamped to the software ceiling");

    elevatorMechanism.setGoalHeight(-5.0);
    assertEquals(
        ElevatorConstants.MIN_HEIGHT,
        elevatorMechanism.getTargetHeightMeters(),
        1e-9,
        "Under-travel request was not clamped to the software floor");
  }

  @Test
  void holdsAgainstGravity() {
    double targetHeight = 0.90;
    elevatorMechanism.setGoalHeight(targetHeight);
    cycle(100);

    double settledHeight = simulatedIO.getPosition();

    // Another second of loop iterations with no new command.
    cycle(50);

    assertEquals(
        settledHeight,
        simulatedIO.getPosition(),
        POSITION_ASSERT_MARGIN_METERS,
        "Carriage drifted while holding position");
  }

  @Test
  @DisplayName("Position command reports finished only after convergence")
  void positionCommandFinishesOnConvergence() {
    var command =
        new ElevatorCommands().createScoreL2Command(elevatorMechanism);
    command.initialize();

    assertFalse(command.isFinished(), "Command reported finished before the carriage moved");

    for (int stepIndex = 0; stepIndex < 100 && !command.isFinished(); stepIndex++) {
      command.execute();
    }

    assertTrue(command.isFinished(), "Command never converged on the mid goal setpoint");
  }
}
