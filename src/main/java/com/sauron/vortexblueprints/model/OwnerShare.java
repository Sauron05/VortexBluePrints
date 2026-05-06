package com.sauron.vortexblueprints.model;

import java.util.UUID;

public record OwnerShare(UUID uuid, String name, double percent) {
}