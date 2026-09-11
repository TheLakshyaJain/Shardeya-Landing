package com.shardeya.builder.project.dto;

import com.shardeya.builder.project.Project;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** PATCH semantics — every field optional, only supplied fields are updated. */
public record ProjectUpdateRequest(
        @Size(min = 2, max = 150, message = "error.project.nameInvalid") String name,
        Project.Type projectType,
        Project.Status status,
        @Size(min = 5, max = 500, message = "error.project.addressInvalid") String address,
        @Size(min = 2, max = 150, message = "error.project.localityInvalid") String locality,
        @Size(min = 2, max = 100, message = "error.project.cityInvalid") String city,
        @Pattern(regexp = "^[A-Z]{2}$", message = "error.project.stateInvalid") String stateCode,
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "error.project.pincodeInvalid") String pincode,
        @Pattern(regexp = "^https://.*", message = "error.project.mapsUrlInvalid") String googleMapsUrl,
        @DecimalMin(value = "0.0001", message = "error.project.areaInvalid")
        @Max(value = 3_000_000, message = "error.project.areaInvalid")
        BigDecimal totalAreaValue,
        String totalAreaUnit,
        @Min(value = 1, message = "error.project.plotCountInvalid")
        @Max(value = 100_000, message = "error.project.plotCountInvalid") Integer declaredPlotCount,
        LocalDate launchDate,
        LocalDate expectedCompletionDate,
        @Size(max = 5000, message = "error.project.descriptionInvalid") String description,
        List<ApprovalTag> approvals,
        @Size(max = 60, message = "error.project.reraInvalid") String reraNumber,
        UUID coverMediaId,
        UUID layoutMediaId,
        UUID brochureMediaId
) {
}
