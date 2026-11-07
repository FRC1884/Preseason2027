package org.Griffins1884.frc2027.subsystems.turret;

import org.wpilib.math.system.DCMotor;
import org.Griffins1884.frc2027.mechanisms.turrets.MechanismTurretIOSim;

public class TurretIOSim extends MechanismTurretIOSim implements TurretIO {
  public TurretIOSim() {
    super(
        DCMotor.getNeoVortex(TurretConstants.SIM_MOTOR_COUNT),
        TurretConstants.GEAR_RATIO,
        TurretConstants.SIM_MOI,
        TurretConstants.INVERTED);
  }
}
