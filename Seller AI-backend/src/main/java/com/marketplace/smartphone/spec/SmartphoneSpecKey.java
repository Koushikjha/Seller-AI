package com.marketplace.smartphone.spec;

import com.marketplace.catalog.core.SpecKey;

import java.util.List;

/** Smartphone's own extraSpecs vocabulary — same contract, different words. */
public enum SmartphoneSpecKey implements SpecKey {

    CHARGING_WATTS("number", "wired fast-charge wattage"),
    WIRELESS_CHARGING("boolean", null),
    IP_RATING("string", "e.g. \"IP68\""),
    FRONT_CAMERA_MP("number", null),
    ULTRAWIDE_CAMERA_MP("number", null),
    TELEPHOTO_ZOOM_X("number", null),
    SIM_SLOTS("number", null),
    ESIM_SUPPORT("boolean", null),
    HEADPHONE_JACK("boolean", null),
    COLOR_OPTIONS("string[]", "e.g. [\"Midnight\", \"Titanium\"]"),
    OS_UPDATE_YEARS("number", "promised major OS versions"),
    WARRANTY_NOTES("string", "beyond brand.default_warranty_months");

    private final String type;
    private final String description;

    SmartphoneSpecKey(String type, String description) {
        this.type = type;
        this.description = description;
    }

    @Override public String key() { return name(); }
    @Override public String type() { return type; }
    @Override public String description() { return description; }

    public static List<SmartphoneSpecKey> vocabulary() { return List.of(values()); }
}
