package org.Griffins1884.frc2027.subsystems.elevator;

import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.controller.ElevatorFeedforward;
import org.wpilib.math.controller.ProfiledPIDController;
import org.wpilib.math.trajectory.TrapezoidProfile;

import static org.Griffins1884.frc2027.subsystems.elevator.ElevatorConstants.MAX_HEIGHT;

public class ElevatorSubsystem extends SubsystemBase {
    private ElevatorIO ioBridge;
    private final ElevatorIO.ElevatorIOInputs inputs = new ElevatorIO.ElevatorIOInputs();

    private final ProfiledPIDController profiledController;
    private final ElevatorFeedforward feedforwardModel;

    private final double targetHeightSetpoint = 0.0;

    public ElevatorSubsystem(ElevatorIO ioBridge) {
        this.ioBridge = ioBridge;
        this.profiledController = getProfiledController((ElevatorConstants.kP).getAsDouble(),
                ElevatorConstants.kI.getAsDouble(),
                ElevatorConstants.kD.getAsDouble());
        this.feedforwardModel = getMotionProfileConstraints((ElevatorConstants.kS).getAsDouble(),
                ElevatorConstants.kV.getAsDouble(),
                ElevatorConstants.kA.getAsDouble(),
                ElevatorConstants.kG.getAsDouble());
    }

    public ProfiledPIDController getProfiledController(double kP, double kI, double kD) {
        return new ProfiledPIDController(kP, kI, kD, new TrapezoidProfile.Constraints(
                ElevatorConstants.MAX_VELOCITY,
                ElevatorConstants.MAX_ACCELERATION
        ));
    }

    public ElevatorFeedforward getMotionProfileConstraints(double kS, double kV, double kA, double kG){
        return new ElevatorFeedforward(kS, kV, kA, kG);
    }

    public void setGoalHeight(double targetHeightSetpoint) {
        targetHeightSetpoint =
                Math.clamp(
                        targetHeightSetpoint,
                        ElevatorConstants.MIN_HEIGHT,
                        MAX_HEIGHT);
        profiledController.setGoal(targetHeightSetpoint);
    }

    @Override
    public void periodic() {
        ioBridge.updateInputs(inputs);

        TrapezoidProfile.State currentProfileState = profiledController.getSetpoint();
        double feedBackVoltage = feedforwardModel.calculate(currentProfileState.velocity);

        double feedForwardVoltage;
        feedForwardVoltage = (ElevatorConstants.kS.getAsDouble() * Math.signum(currentProfileState.velocity))
                + ElevatorConstants.kG.getAsDouble() + (ElevatorConstants.kV.getAsDouble()
                + ElevatorConstants.kA.getAsDouble()) * currentProfileState.velocity;
        double totalDemandVoltage = feedBackVoltage + feedForwardVoltage;

        if (inputs.positionMeters <= ElevatorConstants.MIN_HEIGHT && totalDemandVoltage < 0.0) {
            totalDemandVoltage = 0.0;
        } else if (inputs.positionMeters >= MAX_HEIGHT
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

    public double getTargetHeightMeters() {
        return targetHeightSetpoint;
    }

    public double getPositionMeters() {
        return inputs.positionMeters;
    }

    public double getVelocityMetersPerSec() {
        return inputs.velocityMetersPerSec;
    }

    public double getAppliedVolts() {
        return inputs.appliedVolts;
    }

    public double getSupplyCurrentAmps() {
        return inputs.supplyCurrentAmps;
    }

    public boolean isAtLowerLimit() {
        return Math.abs(ElevatorConstants.MIN_HEIGHT - getPositionMeters()) <= ElevatorConstants.POSITION_TOLERANCE;
    }

    public boolean isAtUpperLimit() {
        return Math.abs(getPositionMeters() - ElevatorConstants.MAX_HEIGHT) <= ElevatorConstants.POSITION_TOLERANCE;
    }

    public boolean isAtTarget() {
        return Math.abs(getPositionMeters() - getTargetHeightMeters()) <= ElevatorConstants.POSITION_TOLERANCE;
    }

}