package org.Griffins1884.frc2027.subsystems.elevator;

import org.Griffins1884.frc2027.util.LoggedTunableNumber;

public final class ElevatorConstants {
  public static final double minHeight = 0.0; // TODO change;
  public static final double maxHeight = 0.75; // TODO change;
  public static final double maxVelocity = 0.0;
  public static final LoggedTunableNumber kV = new LoggedTunableNumber("Elevator/kV", 0.0);
  public static final LoggedTunableNumber kA = new LoggedTunableNumber("Elevator/kA", 0.0);
}
