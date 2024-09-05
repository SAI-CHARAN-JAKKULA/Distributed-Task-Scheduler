package com.umar.taskscheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.umar.taskscheduler.callbacks.AssignmentListener;
import com.umar.taskscheduler.module.GuiceModule;
import com.umar.taskscheduler.resources.Job;
import com.umar.taskscheduler.resources.Worker;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.GuiceBundle;

/**
 * Main application class for the Distributed Task Scheduler.
 * Initializes the application with the necessary configurations, bundles, and modules.
 *
 * @author Umar Mohammad
 */
@Slf4j
public class App extends Application<AppConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    /**
     * Main entry point for the application.
     *
     * @param args Command line arguments passed to the application.
     * @throws Exception if any error occurs during the application start.
     */
    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    /**
     * Initializes the application by adding necessary bundles.
     * This method is responsible for setting up Guice and Swagger bundles.
     *
     * @param bootstrap The Bootstrap object for the Dropwizard application.
     */
    @Override
    public void initialize(Bootstrap<AppConfiguration> bootstrap) {
        // Add Guice and Swagger bundles to the application.
        bootstrap.addBundle(guiceBundle());
        bootstrap.addBundle(
            new SwaggerBundle<>() {
                @Override
                protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
                        AppConfiguration appConfiguration) {
                    return appConfiguration.getSwaggerBundleConfiguration();
                }
            });
    }

    /**
     * Runs the application by registering REST resources.
     *
     * @param c The AppConfiguration object containing the application's configuration.
     * @param e The Environment object representing the current environment of the application.
     */
    @Override
    public void run(AppConfiguration c, Environment e) {
        log.info("Registering REST resources");
        // Register REST resources for Worker and Job services
        e.jersey().register(Worker.class);
        e.jersey().register(Job.class);
    }

    /**
     * Creates a new instance of the Guice module for dependency injection.
     *
     * @return A new GuiceModule instance.
     */
    private GuiceModule createGuiceModule() {
        return new GuiceModule();
    }

    /**
     * Configures and returns the Guice bundle for the application.
     * This method enables automatic configuration of Guice modules.
     *
     * @return A configured GuiceBundle object.
     */
    private GuiceBundle guiceBundle() {
        return GuiceBundle.builder().enableAutoConfig().modules(new GuiceModule()).build();
    }
}
