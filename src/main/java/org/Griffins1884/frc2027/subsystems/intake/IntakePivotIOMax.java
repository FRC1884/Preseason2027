package org.Griffins1884.frc2027.subsystems.intake;

import org.Griffins1884.frc2027.CanIDConstants;
import org.Griffins1884.frc2027.mechanisms.arms.MechanismArmIOSparkMax;

public class IntakePivotIOMax extends MechanismArmIOSparkMax implements IntakePivotIO {
  public IntakePivotIOMax(int id, boolean inverted) {
    super(
        new int[] {id},
        IntakePivotConstants.CURRENT_LIMIT_AMPS,
        IntakePivotConstants.BRAKE_MODE,
        IntakePivotConstants.FORWARD_LIMIT,
        IntakePivotConstants.REVERSE_LIMIT,
        IntakePivotConstants.POSITION_COEFFICIENT,
        CanIDConstants.INTAKE_PIVOT_IO_MAX);
    if (inverted) {
      invert(0);
    }
  }
}
