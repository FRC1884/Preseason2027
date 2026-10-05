package org.Griffins1884.frc2027;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathplanner.lib.auto.AutoBuilder;
import org.wpilib.hardware.hal.HAL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RobotContainerConstructionTest {
  @BeforeAll
  static void initializeHal() {
    assertTrue(HAL.initialize());
  }

  @Test
  void constructsReusableInfrastructureInSimulation() {
    System.setProperty("frc.web.port", "0");
    AutoBuilder.resetForTesting();

    try (RobotContainer ignored = new RobotContainer()) {
      assertTrue(AutoBuilder.isConfigured());
    } finally {
      System.clearProperty("frc.web.port");
    }
  }
}
