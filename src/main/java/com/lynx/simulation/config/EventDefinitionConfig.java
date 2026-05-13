package com.lynx.simulation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "simulation.events")
@Getter
@Setter
public class EventDefinitionConfig {

    private List<EventDefinition> definitions = new ArrayList<>();

    @Getter
    @Setter
    public static class EventDefinition {
        private String eventType;
        private String scope;
        private String target;
        private double magnitude = 1.0;
        private int durationTicks = 10;
        private double weight = 1.0;
        private List<String> headlines = new ArrayList<>();
    }
}
