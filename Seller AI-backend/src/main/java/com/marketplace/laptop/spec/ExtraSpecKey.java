package com.marketplace.laptop.spec;

import com.marketplace.catalog.core.SpecKey;

import java.util.List;

/**
 * Allowed keys for Laptop.extraSpecs (JSONB).
 *
 *  - Drives the merchant "add specification" dropdown in the admin UI.
 *  - Is the ONLY vocabulary the agent is permitted to reference from extraSpecs.
 *  - Any incoming extraSpecs key not in this enum is rejected at create/update.
 *
 * Add a key here and to the dropdown together -- never one without the other.
 */
public enum ExtraSpecKey implements SpecKey {

    PORTS("string[]", "e.g. [\"2x USB-C\", \"1x HDMI 2.1\", \"1x USB-A\"]"),
    WEBCAM_RESOLUTION("string", "e.g. \"1080p\""),
    KEYBOARD_BACKLIGHT("boolean", null),
    WIFI_STANDARD("string", "e.g. \"WiFi 6E\""),
    BLUETOOTH_VERSION("string", "e.g. \"5.3\""),
    COLOR_OPTIONS("string[]", "e.g. [\"Space Gray\", \"Silver\"]"),
    DIMENSIONS_CM("string", "L x W x H"),
    CHASSIS_MATERIAL("string", "e.g. \"Aluminum unibody\", \"Plastic\""),
    SPEAKER_QUALITY("string", "free text, presentational only"),
    FINGERPRINT_SENSOR("boolean", null),
    THUNDERBOLT_SUPPORT("boolean", null),
    WARRANTY_NOTES("string", "beyond brand.default_warranty_months"),
    NUMPAD("boolean", null);

    private final String type;
    private final String description;

    ExtraSpecKey(String type, String description) {
        this.type = type;
        this.description = description;
    }

    @Override public String key() { return name(); }
    @Override public String type() { return type; }
    @Override public String description() { return description; }

    public static List<ExtraSpecKey> vocabulary() {
        return List.of(values());
    }
}
