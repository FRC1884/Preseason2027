package org.Griffins1884.frc2027.commands;

import org.Griffins1884.frc2027.subsystems.elevator.ElevatorSetpoints;
import org.Griffins1884.frc2027.subsystems.elevator.ElevatorSubsystem;
import org.wpilib.command2.Command;

public class ElevatorCommands extends Command {
  public ElevatorPositionCommand createGoalCommand(
      ElevatorSubsystem elevator, double targetHeight) {
    return new ElevatorPositionCommand(elevator, targetHeight);
  }

  public ElevatorPositionCommand createStowCommand(ElevatorSubsystem elevator) {
    return createGoalCommand(elevator, ElevatorSetpoints.startingHeight);
  }

  public ElevatorPositionCommand createScoreL1Command(ElevatorSubsystem elevator) {
    return createGoalCommand(elevator, ElevatorSetpoints.goalMidHeight);
  }

  public ElevatorPositionCommand createScoreL2Command(ElevatorSubsystem elevator) {
    return createGoalCommand(elevator, ElevatorSetpoints.goalHighHeight);
  }

  public static class ElevatorPositionCommand {

    private final ElevatorSubsystem subsystemRef;
    private final double commandedTargetHeight;

    public ElevatorPositionCommand(ElevatorSubsystem targetSubsystem, double requestedHeight) {
      this.subsystemRef = targetSubsystem;
      this.commandedTargetHeight = requestedHeight;
    }

    public void initialize() {
      // Apply target setpoint into the motion profile controller.
      if (subsystemRef == null) return;
      subsystemRef.setGoalHeight(commandedTargetHeight);
    }

    public void execute() {
      // Maintain continuous target tracking demand.
      if (subsystemRef == null) return;
      subsystemRef.periodic();
    }

    public boolean isFinished() {
      if (subsystemRef == null) return false;
      return subsystemRef.isAtTarget();
    }

    public void end(boolean interrupted) {
      if (subsystemRef == null) return;
      if (!interrupted) {
        subsystemRef.setGoalHeight(commandedTargetHeight);
      }
    }
  }
}
