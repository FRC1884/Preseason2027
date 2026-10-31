package org.Griffins1884.frc2027.subsystems.elevator;
import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Pose2d;


public interface ElevatorIO {
    @AutoLog
    public static class ElevatorIOInputs{
        public double positionMeters = 0;
        public double velocityMeterPerSecond = 0;
        public double appliedVolts = 0;
        public double statorCurrentAmps = 0;
        public double supplyCurrentAmps = 0;
        public boolean atLowerLimit = false;
        public boolean atUpperLimit = false;
    }

    public default void updateInputs(ElevatorIOInputs inputs){}
    // Populate mutable telemetry container with latest sensor and electrical state

    public default void setVoltage(int demandVolts){}
    // Command output potential across motor drives [-12.0 to +12.0]

    public default void stopActuators(){
        setVoltage(0);
    }
}
