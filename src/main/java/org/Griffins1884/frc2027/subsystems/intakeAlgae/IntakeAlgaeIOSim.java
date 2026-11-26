package org.Griffins1884.frc2027.subsystems.intakeAlgae;

import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOSim;
import org.wpilib.math.system.DCMotor;

public class IntakeAlgaeIOSim extends MechanismRollerIOSim implements IntakeAlgaeIO {
  public IntakeAlgaeIOSim(DCMotor motorModel, double reduction, double moi) {
    super(motorModel, reduction, moi);
  }
}
