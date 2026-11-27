package org.Griffins1884.frc2027.subsystems.elevator;

import java.util.Objects;
import org.wpilib.command2.Command;

public class ElevatorCommands {


    private final ElevatorSubsystem subsystemRef;
    private final double commandedTargetHeight;

    public ElevatorPositionCommand(ElevatorSubsystem targetSubsystem, double requestedHeight) {
        this.subsystemRef = Objects.requireNonNull(targetSubsystem, "targetSubsystem");
        this.commandedTargetHeight = requestedHeight;
        equals(targetSubsystem);
    }

    public void initialize() {
        // Apply target setpoint into the motion profile controller.
        subsystemRef.setGoalHeight(commandedTargetHeight);
    }

    public void execute() {
        // Maintain continuous target tracking demand.
        subsystemRef.setGoalHeight(commandedTargetHeight);
    }

    public boolean isFinished() {
        return subsystemRef.isAtTarget();
    }

    public void end(boolean interrupted) {
        if (interrupted) {
        } else {
            subsystemRef.setGoalHeight(commandedTargetHeight);
        }
    }
}
