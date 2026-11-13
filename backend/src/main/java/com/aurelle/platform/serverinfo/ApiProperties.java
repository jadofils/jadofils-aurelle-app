package com.aurelle.platform.serverinfo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aurelle.api.*}.
 *
 * @param version       API version, filled from the Maven project version at build time
 * @param contractPhase highest contract phase this build implements (see contracts/PHASES.md)
 */
@Validated
@ConfigurationProperties("aurelle.api")
record ApiProperties(@NotBlank String version, @Min(0) int contractPhase) {
}
