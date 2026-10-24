package org.Griffins1884.frc2027.OI;

import static org.wpilib.driverstation.GenericHID.RumbleType.kBothRumble;
import static org.wpilib.command2.Commands.startEnd;

import org.wpilib.command2.Command;
import org.wpilib.command2.button.CommandXboxController;
import org.wpilib.command2.button.Trigger;
import java.util.function.DoubleSupplier;

public class SimXboxUniversalMap extends CommandXboxController implements DriverMap {
  /**
   * Construct an instance of a controller.
   *
   * @param port The port index on the Driver Station that the controller is plugged into.
   */
  public SimXboxUniversalMap(int port) {
    super(port);
  }

  @Override
  public DoubleSupplier getXAxis() {
    return () -> -getLeftX();
  }

  @Override
  public DoubleSupplier getYAxis() {
    return () -> -getLeftY();
  }

  @Override
  public DoubleSupplier getRotAxis() {
    return () -> -getRightX();
  }

  @Override
  public Trigger resetOdometry() {
    return back();
  }

  @Override
  public Trigger alignWithBall() {
    return new Trigger(() -> this.getLeftTriggerAxis() > 0.5);
  }

  @Override
  public Trigger shootToggle() {
    return new Trigger(() -> this.getRightTriggerAxis() > 0.5);
  }

  @Override
  public Trigger intakeRollersHold() {
    return rightBumper();
  }

  @Override
  public Trigger intakeDeployToggle() {
    return leftBumper();
  }

  @Override
  public Command rumble() {
    return startEnd(
        () -> getHID().setRumble(kBothRumble, 1), () -> getHID().setRumble(kBothRumble, 0));
  }

  public Trigger shooterPivotUp() {
    return y();
  }

  public Trigger shooterPivotDown() {
    return a();
  }

  public Trigger turretLeft() {
    return x();
  }

  public Trigger turretRight() {
    return b();
  }
}
