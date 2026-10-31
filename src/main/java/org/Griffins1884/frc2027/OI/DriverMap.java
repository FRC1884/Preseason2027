package org.Griffins1884.frc2027.OI;

import static org.wpilib.command2.Commands.none;

import org.wpilib.command2.Command;
import org.wpilib.command2.button.Trigger;
import java.util.function.DoubleSupplier;

public interface DriverMap {
  DoubleSupplier getXAxis();

  DoubleSupplier getYAxis();

  DoubleSupplier getRotAxis();

  Trigger resetOdometry();

  Trigger alignWithBall();

  // Placeholder mapping for "start/stop shooting" control.
  Trigger shootToggle();

  // Placeholder mapping for "run intake rollers while held" control.
  Trigger intakeRollersHold();

  // Placeholder mapping for "toggle intake deploy" control.
  Trigger intakeDeployToggle();

  default Trigger leftBackButton() {
    return new Trigger(() -> false);
  }

  default Trigger rightBackButton() {
    return new Trigger(() -> false);
  }

  default Command rumble() {
    return none();
  }

  Trigger shooterPivotUp();

  Trigger shooterPivotDown();

  Trigger turretLeft();

  public Trigger turretRight();
}
