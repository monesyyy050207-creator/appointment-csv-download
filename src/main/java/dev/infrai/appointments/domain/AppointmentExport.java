package dev.infrai.appointments.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public final class AppointmentExport {
    private AppointmentExport() {
    }

    public enum WorkflowStatus {
        REQUESTED, CONFIRMED, COMPLETED, CANCELLED
    }

    public record Appointment(
            @NotBlank String appointmentReference,
            @NotNull OffsetDateTime scheduledAt,
            @NotBlank String serviceLine,
            @NotNull WorkflowStatus status) {
    }

    public record Request(
            @NotBlank String reportReference,
            @NotEmpty List<@Valid Appointment> appointments) {
    }

    public record Result(
            String reportReference,
            int exportedAppointments,
            String downloadUrl,
            OffsetDateTime downloadExpiresAt,
            String operationalNotification) {
    }
}
