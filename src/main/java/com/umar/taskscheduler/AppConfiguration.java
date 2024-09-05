package com.umar.taskscheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import lombok.*;

/**
 * Configuration class for the Distributed Task Scheduler application.
 * This class handles the application's custom configurations, including Swagger.
 *
 * The configurations are read from the application configuration file and
 * mapped to this class. It extends Dropwizard's {@link Configuration} class.
 * 
 * Jackson annotations are used to handle the JSON mapping of the configuration properties.
 * 
 * Lombok annotations such as {@link Data}, {@link Builder}, {@link AllArgsConstructor},
 * and {@link NoArgsConstructor} are used to generate boilerplate code for getter, setter, 
 * constructor, and builder pattern automatically.
 * 
 * Swagger configuration is handled through the {@link SwaggerBundleConfiguration} property.
 * 
 * @author Umar Mohammad
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfiguration extends Configuration {

    /**
     * Swagger configuration for the API documentation.
     * This field is mapped from the configuration file using the {@code swagger} property.
     */
    @JsonProperty("swagger")
    private SwaggerBundleConfiguration swaggerBundleConfiguration;

    /**
     * Getter for the Swagger configuration.
     * 
     * @return the SwaggerBundleConfiguration object containing Swagger settings.
     */
    public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
        return swaggerBundleConfiguration;
    }
}
