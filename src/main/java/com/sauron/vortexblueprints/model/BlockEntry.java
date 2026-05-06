package com.sauron.vortexblueprints.model;

import java.util.Optional;

public record BlockEntry(int relativeX, int relativeY, int relativeZ, String blockData) {

    private static final String SEPARATOR = "|";

    public String serialize() {
        return relativeX + "," + relativeY + "," + relativeZ + SEPARATOR + blockData;
    }

    public static Optional<BlockEntry> deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }
        int separatorIndex = serialized.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            return Optional.empty();
        }
        String[] coordinates = serialized.substring(0, separatorIndex).split(",", 3);
        if (coordinates.length != 3) {
            return Optional.empty();
        }
        try {
            int relativeX = Integer.parseInt(coordinates[0]);
            int relativeY = Integer.parseInt(coordinates[1]);
            int relativeZ = Integer.parseInt(coordinates[2]);
            String blockData = serialized.substring(separatorIndex + SEPARATOR.length());
            return Optional.of(new BlockEntry(relativeX, relativeY, relativeZ, blockData));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}