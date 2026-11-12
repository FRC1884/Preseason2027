package org.Griffins1884.frc2027.subsystems.turret;

import org.Griffins1884.frc2027.mechanisms.turrets.MechanismTurretIOSim;
import org.wpilib.math.system.DCMotor;

public class TurretIOSim extends MechanismTurretIOSim implements TurretIO {
  public TurretIOSim() {
    super(
        DCMotor.getNeoVortex(TurretConstants.SIM_MOTOR_COUNT),
        TurretConstants.GEAR_RATIO,
        TurretConstants.SIM_MOI,
        TurretConstants.INVERTED);
  }
}
