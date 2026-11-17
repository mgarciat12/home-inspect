package com.homeinspect.app.report.dto;

import com.homeinspect.app.report.Occupancy;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.ReportType;
import com.homeinspect.app.report.SoilCondition;
import com.homeinspect.app.report.Weather;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Header of a report: inspection, client, property and conditions. */
@Data
public class ReportHeaderForm {

    /** Chosen when the report is created; cannot be changed afterwards. */
    @NotNull
    private ReportType type = ReportType.FULL;

    /** Other reports to create for the same visit (only when creating). */
    private java.util.Set<ReportType> alsoCreate = java.util.EnumSet.noneOf(ReportType.class);

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime inspectedAt;

    // ---- client ----
    @Size(max = 200)
    private String clientName;
    @Email
    @Size(max = 255)
    private String clientEmail;
    @Size(max = 50)
    private String clientPhone;
    @Size(max = 200)
    private String insuranceCompany;
    @Size(max = 100)
    private String policyNumber;

    // ---- property ----
    @NotBlank
    @Size(max = 300)
    private String address;
    @Size(max = 20)
    private String postalCode;
    @Size(max = 120)
    private String city;
    @Size(max = 120)
    private String county;
    @Size(max = 120)
    private String state;
    private PropertyType propertyType;
    @Min(1000)
    @Max(2100)
    private Integer yearBuilt;
    @Min(0)
    @Max(1_000_000)
    private Integer areaSqft;
    @Min(0)
    @Max(200)
    private Integer floors;
    private Occupancy occupancy;
    @Size(max = 300)
    private String peoplePresent;

    // ---- conditions ----
    private Weather weather;
    @DecimalMin("-40")
    @DecimalMax("140")
    private BigDecimal temperatureF;
    private SoilCondition soilCondition;
}
