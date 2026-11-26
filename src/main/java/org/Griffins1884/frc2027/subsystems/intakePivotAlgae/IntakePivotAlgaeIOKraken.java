package org.Griffins1884.frc2027.subsystems.intakePivotAlgae;

import org.Griffins1884.frc2027.CanIDConstants;
import org.Griffins1884.frc2027.mechanisms.arms.MechanismArmIOKraken;

public class IntakePivotAlgaeIOKraken extends MechanismArmIOKraken implements IntakePivotAlgaeIO {
  public IntakePivotAlgaeIOKraken() {
    super(
        CanIDConstants.ALGAE_INTAKE_PIVOT_IDS,
        IntakePivotAlgaeConstants.CURRENT_LIMIT_AMPS,
        IntakePivotAlgaeConstants.BRAKE,
        IntakePivotAlgaeConstants.FORWARD_SOFT_LIMIT,
        IntakePivotAlgaeConstants.REVERSE_SOFT_LIMIT,
        IntakePivotAlgaeConstants.POSITION_COEFFICIENT,
        IntakePivotAlgaeConstants.INVERTED,
        CanIDConstants.ALGAE_INTAKE_PIVOT_BUS);
  }
}
