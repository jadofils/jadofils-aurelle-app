package com.aurelle.organization;

import java.util.UUID;

/** Read-only view of a location (store, warehouse, online). */
public record LocationSummary(UUID id, String name, String kind, boolean active) {
}
