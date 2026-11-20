package org.Griffins1884.frc2027.subsystems.elevator;

import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.controller.ElevatorFeedforward;
import org.wpilib.math.controller.ProfiledPIDController;

public class ElevatorSubsystem extends SubsystemBase {
    ElevatorIO elevatorIO;
    private final ElevatorIO ioBridge;
    private final ElevatorIO.ElevatorIOInputs inputs = new ElevatorIO.ElevatorIOInputs();

    private final ProfiledPIDController profiledController;
    private final ElevatorFeedforward feedforwardModel;

    private double targetHeightSetpoint = 0.0;
    private boolean profileSeeded = false;


    public ElevatorSubsystem(ElevatorIO ioBridge, ProfiledPIDController profiledController, ElevatorFeedforward feedforwardModel) {
        this.ioBridge = ioBridge;
        this.profiledController = profiledController;
        this.feedforwardModel = feedforwardModel;

    }

    public void setGoalHeight(double targetHeightSetpoint) {
        targetHeightSetpoint =
                Math.clamp(
                        targetHeightSetpoint,
                        ElevatorConstants.minHeight,
                        ElevatorConstants.maxHeight);
        profiledController.setGoal(targetHeightSetpoint);
    }

    @Override
    public void periodic() {
        ioBridge.updateInputs(inputs);
    }


}
