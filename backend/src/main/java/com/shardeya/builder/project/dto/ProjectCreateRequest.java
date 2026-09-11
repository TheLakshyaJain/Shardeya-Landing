package com.shardeya.builder.project.dto;

import com.shardeya.builder.project.Project;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Field validations mirror 03-BUILDER-MODULES.md B-02 §11 exactly. */
public record ProjectCreateRequest(
        @NotBlank(message = "error.project.nameRequired")
        @Size(min = 2, max = 150, message = "error.project.nameInvalid")
        String name,

        @NotNull(message = "error.project.typeRequired")
        Project.Type projectType,

        Project.Status status,

        @NotBlank(message = "error.project.addressRequired")
        @Size(min = 5, max = 500, message = "error.project.addressInvalid")
        String address,

        @NotBlank(message = "error.project.localityRequired")
        @Size(min = 2, max = 150, message = "error.project.localityInvalid")
        String locality,

        @NotBlank(message = "error.project.cityRequired")
        @Size(min = 2, max = 100, message = "error.project.cityInvalid")
        String city,

        @NotBlank(message = "error.project.stateRequired")
        @Pattern(regexp = "^[A-Z]{2}$", message = "error.project.stateInvalid")
        String stateCode,

        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "error.project.pincodeInvalid")
        String pincode,

        @Pattern(regexp = "^https://.*", message = "error.project.mapsUrlInvalid")
        String googleMapsUrl,

        BigDecimal latitude,
        BigDecimal longitude,

        @NotNull(message = "error.project.areaRequired")
        @DecimalMin(value = "0.0001", message = "error.project.areaInvalid")
        @Max(value = 3_000_000, message = "error.project.areaInvalid")
        BigDecimal totalAreaValue,

        @NotBlank(message = "error.project.areaUnitRequired")
        String totalAreaUnit,

        @NotNull(message = "error.project.plotCountRequired")
        @Min(value = 1, message = "error.project.plotCountInvalid")
        @Max(value = 100_000, message = "error.project.plotCountInvalid")
        Integer declaredPlotCount,

        LocalDate launchDate,
        LocalDate expectedCompletionDate,

        @Size(max = 5000, message = "error.project.descriptionInvalid")
        String description,

        List<ApprovalTag> approvals,

        @Size(max = 60, message = "error.project.reraInvalid")
        String reraNumber,

        UUID coverMediaId,
        UUID layoutMediaId,
        UUID brochureMediaId
) {
}
