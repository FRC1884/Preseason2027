package org.Griffins1884.frc2027.subsystems.shooter;

import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOSim;
import org.wpilib.math.system.DCMotor;

public class ShooterIOSim extends MechanismRollerIOSim implements ShooterIO {
  public ShooterIOSim(DCMotor motorModel, double reduction, double moi) {
    super(motorModel, reduction, moi);
  }
}
