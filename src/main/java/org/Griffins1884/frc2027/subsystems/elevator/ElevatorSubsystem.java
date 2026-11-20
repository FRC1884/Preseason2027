package org.Griffins1884.frc2027.subsystems.elevator;

import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.controller.ElevatorFeedforward;
import org.wpilib.math.controller.ProfiledPIDController;
import org.wpilib.math.trajectory.TrapezoidProfile;

public class ElevatorSubsystem extends SubsystemBase {
    ElevatorIO elevatorIO;
    private final ElevatorIO ioBridge;
    private final ElevatorIO.ElevatorIOInputs inputs = new ElevatorIO.ElevatorIOInputs();

    private ProfiledPIDController profiledController;
    private ElevatorFeedforward feedforwardModel;

    private double targetHeightSetpoint = 0.0;
    private boolean profileSeeded = false;


    public ElevatorSubsystem(ElevatorIO ioBridge, ProfiledPIDController profiledController, ElevatorFeedforward feedforwardModel) {
        this.ioBridge = ioBridge;
        this.profiledController = profiledController;
        this.feedforwardModel = feedforwardModel;
    }

    public ProfiledPIDController getProfiledController(double kP, double kI, double kD) {
        return new ProfiledPIDController(kP, kI, kD, new TrapezoidProfile.Constraints(
                ElevatorConstants.MAX_VELOCITY,
                ElevatorConstants.MAX_ACCELERATION
        ));
    }




    public void setGoalHeight(double targetHeightSetpoint) {
        targetHeightSetpoint =
                Math.clamp(
                        targetHeightSetpoint,
                        ElevatorConstants.MIN_HEIGHT,
                        ElevatorConstants.MAX_HEIGHT);
        profiledController.setGoal(targetHeightSetpoint);
    }

    @Override
    public void periodic() {
        ioBridge.updateInputs(inputs);

        TrapezoidProfile.State currentProfileState = profiledController.getSetpoint();
        double feedforwardVoltage = feedforwardModel.calculate(currentProfileState.velocity);

        double feedbackVoltage = 0;
        double totalDemandVoltage = feedbackVoltage + feedforwardVoltage;

        if (inputs.positionMeters <= ElevatorConstants.MIN_HEIGHT && totalDemandVoltage < 0.0) {
            totalDemandVoltage = 0.0;
        } else if (inputs.positionMeters >= ElevatorConstants.MAX_HEIGHT
                && totalDemandVoltage > 0.0) {
            totalDemandVoltage = 0.0;
        }

        totalDemandVoltage =
                Math.clamp(
                        totalDemandVoltage,
                        -ElevatorConstants.MAX_MOTOR_VOLTS,
                        ElevatorConstants.MAX_MOTOR_VOLTS);
        ioBridge.setVoltage(totalDemandVoltage);

    }



}
