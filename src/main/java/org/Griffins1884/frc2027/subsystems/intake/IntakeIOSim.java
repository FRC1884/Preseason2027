package org.Griffins1884.frc2027.subsystems.intake;

import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOSim;
import org.wpilib.math.system.DCMotor;

public class IntakeIOSim extends MechanismRollerIOSim implements IntakeIO {
  public IntakeIOSim(DCMotor motorModel, double reduction, double moi) {
    super(motorModel, reduction, moi);
  }
}
