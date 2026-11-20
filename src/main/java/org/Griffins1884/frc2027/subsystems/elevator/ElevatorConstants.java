package org.Griffins1884.frc2027.subsystems.elevator;

import org.Griffins1884.frc2027.util.LoggedTunableNumber;

public final class ElevatorConstants {
  public static final double MIN_HEIGHT = 0.0; // TODO change;
  public static final double MAX_HEIGHT = 0.75; // TODO change;
  public static final double MAX_VELOCITY = 0.5; // TODO change;
  public static final LoggedTunableNumber kV = new LoggedTunableNumber("Elevator/kV", 7.1);
  public static final LoggedTunableNumber kA = new LoggedTunableNumber("Elevator/kA", 0.12);
  public static final LoggedTunableNumber kS = new LoggedTunableNumber("Elevator/kS", 0.15);
  public static final LoggedTunableNumber kG = new LoggedTunableNumber("Elevator/kG", 0.42);
  public static final LoggedTunableNumber kP = new LoggedTunableNumber("Elevator/kP", 42.0);
  public static final LoggedTunableNumber kI = new LoggedTunableNumber("Elevator/kI", 0.0);
  public static final LoggedTunableNumber kD = new LoggedTunableNumber("Elevator/kD", 1.2);

}
