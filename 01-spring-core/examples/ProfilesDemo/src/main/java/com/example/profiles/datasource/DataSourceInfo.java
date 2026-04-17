/*
 * Simple value object describing a data source configuration.
 * Returned by profile-specific @Bean methods in DataSourceConfig.
 */
package com.example.profiles.datasource;

public record DataSourceInfo(String type, String url) {
    public String describe() {
        return type + " @ " + url;
    }
}
