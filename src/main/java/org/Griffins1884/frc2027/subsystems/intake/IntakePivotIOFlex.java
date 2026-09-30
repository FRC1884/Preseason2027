package org.Griffins1884.frc2027.subsystems.intake;

import org.Griffins1884.frc2027.CanIDConstants;
import org.Griffins1884.frc2027.mechanisms.arms.MechanismArmIOSparkFlex;

public class IntakePivotIOFlex extends MechanismArmIOSparkFlex implements IntakePivotIO {
  public IntakePivotIOFlex(int id, boolean inverted) {
    super(
        new int[] {id},
        IntakePivotConstants.CURRENT_LIMIT_AMPS,
        IntakePivotConstants.BRAKE_MODE,
        IntakePivotConstants.FORWARD_LIMIT,
        IntakePivotConstants.REVERSE_LIMIT,
        IntakePivotConstants.POSITION_COEFFICIENT,
        CanIDConstants.INTAKE_PIVOT_IO_FLEX);
    if (inverted) {
      invert(0);
    }
  }
}
