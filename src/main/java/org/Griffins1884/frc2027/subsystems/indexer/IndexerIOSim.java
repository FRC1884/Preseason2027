package org.Griffins1884.frc2027.subsystems.indexer;

import org.wpilib.math.system.DCMotor;
import org.Griffins1884.frc2027.mechanisms.rollers.MechanismRollerIOSim;

public class IndexerIOSim extends MechanismRollerIOSim implements IndexerIO {
  public IndexerIOSim(DCMotor motorModel, double reduction, double moi) {
    super(motorModel, reduction, moi);
  }
}
