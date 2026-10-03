package org.Griffins1884.frc2027.OI;

import static org.wpilib.driverstation.GenericHID.RumbleType.*;
import static org.wpilib.command2.Commands.startEnd;
import org.wpilib.command2.Commands;

import org.wpilib.command2.Command;
import org.wpilib.command2.ParallelCommandGroup;
import org.wpilib.command2.button.CommandNiDsPS5Controller;
import org.wpilib.command2.button.InternalButton;
import org.wpilib.command2.button.Trigger;
import java.util.function.DoubleSupplier;

public class PS5DriverMap extends CommandNiDsPS5Controller implements DriverMap {
  /**
   * Construct an instance of a controller.
   *
   * @param port The port index on the Driver Station that the controller is plugged into.
   */
  public PS5DriverMap(int port) {
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
    return options();
  }

  @Override
  public Trigger alignWithBall() {
    return circle();
  }

  @Override
  public Trigger shootToggle() {
    return cross();
  }

  @Override
  public Trigger intakeRollersHold() {
    return R1();
  }

  @Override
  public Trigger intakeDeployToggle() {
    return triangle();
  }

  //InternalButton as Placeholder
  public Trigger shooterPivotUp() {
    return new InternalButton();
  }

  public Trigger shooterPivotDown() {
    return new InternalButton();
  }

  public Trigger turretLeft() {
    return new InternalButton();
  }

  public Trigger turretRight() {
    return new InternalButton();
  }

  public ParallelCommandGroup generateParallelGroup(double value){
    Command leftRumble = Commands.runOnce(()-> getHID().setRumble(LEFT_RUMBLE, value));
    Command leftTriggerRumble = Commands.runOnce(() -> getHID().setRumble(LEFT_TRIGGER_RUMBLE, value));

    Command rightRumble = Commands.runOnce(()-> getHID().setRumble(RIGHT_RUMBLE, value));
    Command rightTriggerRumble = Commands.runOnce(() -> getHID().setRumble(RIGHT_TRIGGER_RUMBLE, value));

    ParallelCommandGroup hidGroup = leftRumble.alongWith(leftTriggerRumble).alongWith(rightRumble).alongWith(rightTriggerRumble);

    return hidGroup;
  }

  @Override
  public Command rumble() {
    return startEnd(
        () -> generateParallelGroup(1), () -> generateParallelGroup(0));
  }
}
