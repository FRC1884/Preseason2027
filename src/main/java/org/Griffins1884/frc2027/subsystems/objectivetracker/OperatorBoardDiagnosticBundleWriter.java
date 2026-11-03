package org.Griffins1884.frc2027.subsystems.objectivetracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class OperatorBoardDiagnosticBundleWriter {
  private static final DateTimeFormatter BUNDLE_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final OperatorBoardPersistence persistence;
  private String lastFingerprint = "";
  private OperatorBoardDataModels.DiagnosticBundleManifest lastManifest;

  public OperatorBoardDiagnosticBundleWriter(OperatorBoardPersistence persistence) {
    this.persistence = persistence;
  }

  public synchronized OperatorBoardDataModels.DiagnosticBundleManifest maybeWrite(
      OperatorBoardDiagnosticSnapshot snapshot) {
    String fingerprint = snapshot.fingerprint();
    if (fingerprint.equals(lastFingerprint)) {
      if (lastManifest == null) {
        lastManifest = persistence.readLatestDiagnosticManifest().orElseGet(() -> write(snapshot));
      }
      return lastManifest;
    }
    lastFingerprint = fingerprint;
    lastManifest = write(snapshot);
    return lastManifest;
  }

  public synchronized OperatorBoardDataModels.DiagnosticBundleManifest forceWrite(
      OperatorBoardDiagnosticSnapshot snapshot) {
    lastFingerprint = snapshot.fingerprint();
    lastManifest = write(snapshot);
    return lastManifest;
  }

  private OperatorBoardDataModels.DiagnosticBundleManifest write(
      OperatorBoardDiagnosticSnapshot snapshot) {
    persistence.initialize();
    String bundleId = BUNDLE_TIME.format(Instant.now()) + "-" + snapshot.overallStatus();
    Path bundleDir = persistence.diagnosticBundlesRoot().resolve(bundleId);
    try {
      Files.createDirectories(bundleDir);
      writeJson(
          bundleDir.resolve("summary.json"),
          new OperatorBoardDataModels.DiagnosticBundleSummary(
              OperatorBoardDataModels.SCHEMA_VERSION,
              OperatorBoardDataModels.metadata("diagnosticSummary", bundleId, "Diagnostic Summary"),
              snapshot.overallStatus(),
              snapshot.systemSummary(),
              snapshot.mode(),
              snapshot.actionStatus(),
              snapshot.autoSummary(),
              snapshot.mechanismSummary(),
              snapshot.runtimeProfileStatus()));
      writeJson(
          bundleDir.resolve("observed-data.json"),
          new OperatorBoardDataModels.DiagnosticObservedData(
              snapshot.systemCheck(),
              snapshot.autoCheck(),
              snapshot.ntDiagnostics(),
              snapshot.mechanismStatus(),
              snapshot.actionTrace(),
              snapshot.runtimeProfile(),
              snapshot.selectedAuto(),
              snapshot.autoQueue(),
              snapshot.subsystemDescriptions()));
      writeRawJson(bundleDir.resolve("networktables_snapshot.json"), snapshot.ntDiagnostics());
      writeRawJson(bundleDir.resolve("subsystems.json"), snapshot.mechanismStatus());
      writeRawJson(bundleDir.resolve("alerts.json"), snapshot.systemCheck());
      writeRawJson(bundleDir.resolve("auto_context.json"), snapshot.autoCheck());
      writeRawJson(
          bundleDir.resolve("subsystem_descriptions.json"), snapshot.subsystemDescriptions());
      Files.writeString(
          bundleDir.resolve("report.md"), buildMarkdownReport(snapshot), StandardCharsets.UTF_8);

      List<String> files =
          List.of(
              "summary.json",
              "observed-data.json",
              "networktables_snapshot.json",
              "subsystems.json",
              "alerts.json",
              "auto_context.json",
              "subsystem_descriptions.json",
              "report.md");
      OperatorBoardDataModels.DiagnosticBundleManifest manifest =
          new OperatorBoardDataModels.DiagnosticBundleManifest(
              OperatorBoardDataModels.SCHEMA_VERSION,
              OperatorBoardDataModels.metadata("diagnosticBundle", bundleId, "Diagnostic Bundle"),
              bundleId,
              bundleDir.toString(),
              snapshot.overallStatus(),
              files);
      writeJson(bundleDir.resolve("manifest.json"), manifest);
      persistence.writeLatestDiagnosticManifest(manifest);
      return manifest;
    } catch (IOException ex) {
      DriverStationErrors.reportError(
          "Failed to write diagnostic bundle: " + ex.getMessage(), ex.getStackTrace());
      throw new UncheckedIOException(ex);
    }
  }

  private void writeJson(Path target, Object value) throws IOException {
    OperatorBoardPersistence.JSON.writeValue(target.toFile(), value);
  }

  private void writeRawJson(Path target, String value) throws IOException {
    String normalized = value == null || value.isBlank() ? "{}" : value;
    Files.writeString(target, normalized, StandardCharsets.UTF_8);
  }

  private String buildMarkdownReport(OperatorBoardDiagnosticSnapshot snapshot) {
    List<String> lines = new ArrayList<>();
    lines.add("# Operator Board Diagnostic Bundle");
    lines.add("");
    lines.add("- Generated: " + Instant.now());
    lines.add("- Overall status: " + snapshot.overallStatus());
    lines.add("- System summary: " + snapshot.systemSummary());
    lines.add("- Auto summary: " + snapshot.autoSummary());
    lines.add("- Mechanism summary: " + snapshot.mechanismSummary());
    lines.add("- Runtime profile status: " + snapshot.runtimeProfileStatus());
    lines.add("- Mode: " + snapshot.mode());
    lines.add("");
    lines.add("## Action Trace");
    lines.add("");
    lines.add("```json");
    lines.add(
        snapshot.actionTrace() == null || snapshot.actionTrace().isBlank()
            ? "{}"
            : snapshot.actionTrace());
    lines.add("```");
    lines.add("");
    lines.add("## Recommended Next Checks");
    lines.add("");
    lines.add("- Review `summary.json` for the machine-readable verdict.");
    lines.add(
        "- Compare `networktables_snapshot.json` against expected mode, queue, and health state.");
    lines.add(
        "- Use `subsystem_descriptions.json` to compare expected subsystem motion with the observed report.");
    return String.join("\n", lines);
  }

  public record OperatorBoardDiagnosticSnapshot(
      String overallStatus,
      String systemSummary,
      String autoSummary,
      String mechanismSummary,
      String runtimeProfileStatus,
      String actionStatus,
      String mode,
      String systemCheck,
      String autoCheck,
      String ntDiagnostics,
      String mechanismStatus,
      String actionTrace,
      String runtimeProfile,
      String selectedAuto,
      String autoQueue,
      String subsystemDescriptions) {

    public String fingerprint() {
      return String.join(
          "|",
          normalize(overallStatus),
          normalize(systemSummary),
          normalize(autoSummary),
          normalize(mechanismSummary),
          normalize(runtimeProfileStatus),
          normalize(actionStatus),
          normalize(mode),
          normalize(systemCheck),
          normalize(autoCheck),
          normalize(ntDiagnostics),
          normalize(mechanismStatus),
          normalize(actionTrace),
          normalize(runtimeProfile),
          normalize(selectedAuto),
          normalize(autoQueue),
          normalize(subsystemDescriptions));
    }

    private static String normalize(String value) {
      if (value == null || value.isBlank()) {
        return "";
      }
      try {
        JsonNode node = OperatorBoardPersistence.JSON.readTree(value);
        return OperatorBoardPersistence.JSON.writeValueAsString(withoutTimestamps(node));
      } catch (IOException ex) {
        return value;
      }
    }

    private static JsonNode withoutTimestamps(JsonNode node) {
      if (node.isObject()) {
        ObjectNode normalized = OperatorBoardPersistence.JSON.createObjectNode();
        node.fields()
            .forEachRemaining(
                field -> {
                  if (!field.getKey().toLowerCase(java.util.Locale.ROOT).contains("timestamp")) {
                    normalized.set(field.getKey(), withoutTimestamps(field.getValue()));
                  }
                });
        return normalized;
      }
      if (node.isArray()) {
        ArrayNode normalized = OperatorBoardPersistence.JSON.createArrayNode();
        node.forEach(value -> normalized.add(withoutTimestamps(value)));
        return normalized;
      }
      return node;
    }
  }
}
