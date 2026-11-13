package com.aurelle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Seed data must never load in production (owner rule). */
class SeedProfileTest {

    @Test
    void onlyNonProductionProfilesIncludeSeedData() {
        Properties props = load("application.yml");

        assertThat(props.getProperty("spring.profiles.group.dev")).isEqualTo("seed");
        assertThat(props.getProperty("spring.profiles.group.test")).isEqualTo("seed");
        assertThat(props.getProperty("spring.profiles.group.testing")).isEqualTo("seed");
        assertThat(props.getProperty("spring.profiles.group.staging")).isEqualTo("seed");
        assertThat(props.stringPropertyNames()).noneMatch(key -> key.startsWith("spring.profiles.group.prod"));
        assertThat(props.getProperty("spring.flyway.locations")).doesNotContain("seed");
    }

    @Test
    void productionProfileDoesNotTouchFlywayLocations() {
        assertThat(load("application-prod.yml").stringPropertyNames()).noneMatch(key -> key.startsWith("spring.flyway"));
    }

    @Test
    void seedProfileAddsTheSeedLocation() {
        assertThat(load("application-seed.yml").getProperty("spring.flyway.locations"))
            .isEqualTo("classpath:db/migration,classpath:db/seed");
    }

    private static Properties load(String resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        return yaml.getObject();
    }
}
