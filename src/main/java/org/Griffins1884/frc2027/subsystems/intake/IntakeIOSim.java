package org.Griffins1884.frc2027.subsystems.intake;

import org.wpilib.math.system.DCMotor;
import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOSim;

public class IntakeIOSim extends MechanismRollerIOSim implements IntakeIO {
  public IntakeIOSim(DCMotor motorModel, double reduction, double moi) {
    super(motorModel, reduction, moi);
  }
}
