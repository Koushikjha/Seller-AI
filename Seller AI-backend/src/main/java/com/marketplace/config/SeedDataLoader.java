package com.marketplace.config;

import com.marketplace.catalog.entity.Brand;
import com.marketplace.catalog.entity.Cpu;
import com.marketplace.catalog.entity.Gpu;
import com.marketplace.catalog.entity.SubBrand;
import com.marketplace.catalog.repository.BrandRepository;
import com.marketplace.catalog.repository.CpuRepository;
import com.marketplace.catalog.repository.GpuRepository;
import com.marketplace.catalog.repository.SubBrandRepository;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.smartphone.entity.Smartphone;
import com.marketplace.smartphone.repository.SmartphoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a small but deliberately uneven catalog: different segments, different
 * price tiers, one deliberately out-of-stock model and one zero-discount model,
 * so the agent's edge cases (no MacBook, out of stock, non-negotiable) are all
 * exercisable on a fresh database.
 */
@Configuration
@ConditionalOnProperty(name = "marketplace.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    @Bean
    ApplicationRunner seedRunner(BrandRepository brands, SubBrandRepository subBrands,
                                 CpuRepository cpus, GpuRepository gpus,
                                 LaptopRepository laptops, SmartphoneRepository phones) {
        return args -> seed(brands, subBrands, cpus, gpus, laptops, phones);
    }

    void seed(BrandRepository brands, SubBrandRepository subBrands, CpuRepository cpus,
              GpuRepository gpus, LaptopRepository laptops, SmartphoneRepository phones) {

        if (laptops.count() > 0) {
            log.info("Seed skipped: catalog already populated ({} laptops)", laptops.count());
            return;
        }

        // ---------------- brands ----------------
        Brand asus   = brands.save(brand("ASUS", "Taiwan", "STANDARD", 12, "performance-per-rupee"));
        Brand lenovo = brands.save(brand("Lenovo", "China", "PREMIUM", 12, "reliability and service network"));
        Brand hp     = brands.save(brand("HP", "USA", "STANDARD", 12, "mainstream all-rounder"));
        Brand dell   = brands.save(brand("Dell", "USA", "PREMIUM", 12, "premium-build"));
        Brand acer   = brands.save(brand("Acer", "Taiwan", "BUDGET", 12, "value-for-money"));

        // ---------------- sub-brands ----------------
        SubBrand rog      = subBrands.save(sub(asus, "ROG", "GAMING", "FLAGSHIP", "aluminum-magnesium chassis", "enthusiast gamers"));
        SubBrand tuf      = subBrands.save(sub(asus, "TUF", "GAMING", "MID", "reinforced plastic chassis", "students who game"));
        SubBrand zenbook  = subBrands.save(sub(asus, "ZenBook", "ULTRABOOK", "MID", "aluminum unibody", "commuters"));
        SubBrand thinkpad = subBrands.save(sub(lenovo, "ThinkPad", "BUSINESS", "MID", "carbon-fibre reinforced", "professionals"));
        SubBrand ideapad  = subBrands.save(sub(lenovo, "IdeaPad", "BUDGET", "ENTRY", "plastic chassis", "students"));
        SubBrand legion   = subBrands.save(sub(lenovo, "Legion", "GAMING", "FLAGSHIP", "aluminum lid", "enthusiast gamers"));
        SubBrand victus   = subBrands.save(sub(hp, "Victus", "GAMING", "MID", "plastic chassis", "casual gamers"));
        SubBrand pavilion = subBrands.save(sub(hp, "Pavilion", "BUDGET", "ENTRY", "plastic chassis", "home users"));
        SubBrand xps      = subBrands.save(sub(dell, "XPS", "ULTRABOOK", "FLAGSHIP", "machined aluminum", "creative professionals"));
        SubBrand aspire   = subBrands.save(sub(acer, "Aspire", "BUDGET", "ENTRY", "plastic chassis", "first-time buyers"));
        SubBrand galaxy   = subBrands.save(sub(lenovo, "Moto Edge", "FLAGSHIP", "MID", "glass back", "phone upgraders"));

        // ---------------- processors ----------------
        Cpu i3   = cpus.save(cpu("Intel Core i3-1215U", "Intel", 6, 8, "1.2", "4.4", 15, "ENTRY"));
        Cpu i5   = cpus.save(cpu("Intel Core i5-13420H", "Intel", 8, 12, "2.1", "4.6", 45, "MID"));
        Cpu i7hx = cpus.save(cpu("Intel Core i7-13650HX", "Intel", 14, 20, "2.6", "4.9", 55, "HIGH"));
        Cpu ultra7 = cpus.save(cpu("Intel Core Ultra 7 155H", "Intel", 16, 22, "1.4", "4.8", 28, "HIGH"));
        Cpu r5   = cpus.save(cpu("AMD Ryzen 5 7535HS", "AMD", 6, 12, "3.3", "4.5", 35, "MID"));
        Cpu r7   = cpus.save(cpu("AMD Ryzen 7 7840HS", "AMD", 8, 16, "3.8", "5.1", 45, "HIGH"));
        Cpu sd8g3 = cpus.save(cpu("Snapdragon 8 Gen 3", "Qualcomm", 8, 8, "2.3", "3.3", 8, "FLAGSHIP"));
        Cpu dim7200 = cpus.save(cpu("MediaTek Dimensity 7200", "MediaTek", 8, 8, "2.0", "2.8", 6, "MID"));

        // ---------------- graphics ----------------
        Gpu rtx4070 = gpus.save(gpu("RTX 4070 Laptop", "NVIDIA", 8, "HIGH", false));
        Gpu rtx4060 = gpus.save(gpu("RTX 4060 Laptop", "NVIDIA", 8, "HIGH", false));
        Gpu rtx4050 = gpus.save(gpu("RTX 4050 Laptop", "NVIDIA", 6, "MID", false));
        Gpu irisXe  = gpus.save(gpu("Intel Iris Xe", "Intel", 0, "ENTRY", true));
        Gpu arc     = gpus.save(gpu("Intel Arc Graphics", "Intel", 0, "MID", true));
        Gpu radeon  = gpus.save(gpu("AMD Radeon 780M", "AMD", 0, "MID", true));

        // ---------------- laptops ----------------
        laptops.save(laptop(aspire, i3, irisXe, "Aspire 3 A315", "38990", "4.00", 12,
                8, "DDR4", 512, "SSD", "15.6", "FHD", 60, false, "1.78", 7, "Windows 11 Home", 2024,
                specs("CHASSIS_MATERIAL", "Plastic", "NUMPAD", true, "WEBCAM_RESOLUTION", "720p",
                        "PORTS", List.of("1x USB-C", "2x USB-A", "1x HDMI"))));

        laptops.save(laptop(ideapad, r5, radeon, "IdeaPad Slim 5", "54990", "5.00", 8,
                16, "DDR5", 512, "SSD", "14.0", "FHD", 60, false, "1.46", 9, "Windows 11 Home", 2024,
                specs("CHASSIS_MATERIAL", "Aluminum top cover", "FINGERPRINT_SENSOR", true,
                        "KEYBOARD_BACKLIGHT", true, "WIFI_STANDARD", "WiFi 6")));

        laptops.save(laptop(pavilion, i5, irisXe, "Pavilion 14-ec", "59990", "6.00", 5,
                16, "DDR4", 512, "SSD", "14.0", "FHD", 60, true, "1.41", 8, "Windows 11 Home", 2024,
                specs("KEYBOARD_BACKLIGHT", true, "WEBCAM_RESOLUTION", "1080p",
                        "COLOR_OPTIONS", List.of("Natural Silver", "Warm Gold"))));

        laptops.save(laptop(victus, i5, rtx4050, "Victus 15-fa", "72990", "5.00", 6,
                16, "DDR4", 512, "SSD", "15.6", "FHD", 144, false, "2.29", 6, "Windows 11 Home", 2024,
                specs("KEYBOARD_BACKLIGHT", true, "NUMPAD", true, "WIFI_STANDARD", "WiFi 6",
                        "PORTS", List.of("1x USB-C", "3x USB-A", "1x HDMI 2.1", "1x RJ-45"))));

        laptops.save(laptop(tuf, r7, rtx4060, "TUF Gaming A15 FA507", "89990", "7.00", 4,
                16, "DDR5", 1024, "SSD", "15.6", "FHD", 144, false, "2.20", 7, "Windows 11 Home", 2024,
                specs("KEYBOARD_BACKLIGHT", true, "NUMPAD", true, "CHASSIS_MATERIAL", "Reinforced plastic",
                        "WIFI_STANDARD", "WiFi 6E", "WARRANTY_NOTES", "1 year onsite + 1 year ADP")));

        laptops.save(laptop(thinkpad, ultra7, arc, "ThinkPad E14 Gen 6", "94990", "4.00", 7,
                16, "DDR5", 512, "SSD", "14.0", "QHD", 60, false, "1.41", 11, "Windows 11 Pro", 2025,
                specs("FINGERPRINT_SENSOR", true, "THUNDERBOLT_SUPPORT", true, "KEYBOARD_BACKLIGHT", true,
                        "WEBCAM_RESOLUTION", "1080p", "CHASSIS_MATERIAL", "Aluminum",
                        "WARRANTY_NOTES", "3 year depot available as an upgrade")));

        laptops.save(laptop(zenbook, ultra7, arc, "ZenBook 14 OLED UX3405", "99990", "6.00", 5,
                16, "DDR5", 1024, "SSD", "14.0", "OLED", 120, true, "1.20", 13, "Windows 11 Home", 2025,
                specs("CHASSIS_MATERIAL", "Aluminum unibody", "THUNDERBOLT_SUPPORT", true,
                        "SPEAKER_QUALITY", "Harman Kardon tuned, above average for the class",
                        "COLOR_OPTIONS", List.of("Basalt Grey", "Ponder Blue"), "KEYBOARD_BACKLIGHT", true)));

        laptops.save(laptop(legion, i7hx, rtx4060, "Legion 5i Pro", "124990", "6.00", 3,
                16, "DDR5", 1024, "SSD", "16.0", "QHD", 165, false, "2.40", 6, "Windows 11 Home", 2025,
                specs("KEYBOARD_BACKLIGHT", true, "NUMPAD", true, "WIFI_STANDARD", "WiFi 6E",
                        "PORTS", List.of("2x USB-C", "3x USB-A", "1x HDMI 2.1", "1x RJ-45"))));

        // Deliberately out of stock: exercises the failed-close path.
        laptops.save(laptop(rog, i7hx, rtx4070, "ROG Strix G16 G614", "154990", "5.00", 0,
                32, "DDR5", 1024, "SSD", "16.0", "QHD", 240, false, "2.50", 5, "Windows 11 Home", 2025,
                specs("KEYBOARD_BACKLIGHT", true, "WIFI_STANDARD", "WiFi 6E",
                        "CHASSIS_MATERIAL", "Aluminum-magnesium alloy", "THUNDERBOLT_SUPPORT", true)));

        // Deliberately non-negotiable: exercises "I can't discount this one".
        laptops.save(laptop(xps, ultra7, arc, "XPS 14 9440", "184990", "0.00", 2,
                32, "DDR5", 1024, "SSD", "14.5", "OLED", 120, true, "1.68", 12, "Windows 11 Pro", 2025,
                specs("CHASSIS_MATERIAL", "Machined aluminum", "THUNDERBOLT_SUPPORT", true,
                        "SPEAKER_QUALITY", "Quad-speaker, best in this lineup",
                        "FINGERPRINT_SENSOR", true, "WARRANTY_NOTES", "Premium support included")));

        // ---------------- smartphones (thin second device type) ----------------
        phones.save(phone(galaxy, sd8g3, "Edge 60 Pro", "64990", "6.00", 9,
                12, 256, "6.7", 144, 5000, 50, "Android 15", 2025,
                specs("CHARGING_WATTS", 125, "IP_RATING", "IP68", "WIRELESS_CHARGING", true,
                        "ESIM_SUPPORT", true, "OS_UPDATE_YEARS", 4)));

        phones.save(phone(galaxy, dim7200, "Edge 50 Fusion", "24990", "8.00", 15,
                8, 128, "6.7", 144, 5000, 50, "Android 15", 2024,
                specs("CHARGING_WATTS", 68, "IP_RATING", "IP68", "HEADPHONE_JACK", false,
                        "OS_UPDATE_YEARS", 3)));

        phones.save(phone(galaxy, dim7200, "Edge 50 Neo", "29990", "5.00", 0,
                8, 256, "6.4", 120, 4310, 50, "Android 15", 2025,
                specs("CHARGING_WATTS", 68, "IP_RATING", "IP68", "WIRELESS_CHARGING", true,
                        "OS_UPDATE_YEARS", 5)));

        log.info("Seeded {} laptops and {} smartphones across {} brands",
                laptops.count(), phones.count(), brands.count());
    }

    // ------------------------------------------------------------------
    // builders
    // ------------------------------------------------------------------

    private Brand brand(String name, String country, String tier, int warranty, String positioning) {
        Brand b = new Brand();
        b.setName(name);
        b.setCountryOfOrigin(country);
        b.setSupportTier(tier);
        b.setDefaultWarrantyMonths(warranty);
        b.setBrandPositioning(positioning);
        return b;
    }

    private SubBrand sub(Brand brand, String name, String segment, String priceTier,
                         String build, String persona) {
        SubBrand sb = new SubBrand();
        sb.setBrand(brand);
        sb.setName(name);
        sb.setSegment(segment);
        sb.setPriceTier(priceTier);
        sb.setBuildQualityTier(build);
        sb.setTargetPersona(persona);
        return sb;
    }

    private Cpu cpu(String name, String mfr, int cores, int threads, String base, String boost,
                    int tdp, String tier) {
        Cpu c = new Cpu();
        c.setName(name);
        c.setManufacturer(mfr);
        c.setCores(cores);
        c.setThreads(threads);
        c.setBaseClockGhz(new BigDecimal(base));
        c.setBoostClockGhz(new BigDecimal(boost));
        c.setTdpWatts(tdp);
        c.setBenchmarkTier(tier);
        return c;
    }

    private Gpu gpu(String name, String mfr, int vram, String tier, boolean integrated) {
        Gpu g = new Gpu();
        g.setName(name);
        g.setManufacturer(mfr);
        g.setVramGb(vram);
        g.setBenchmarkTier(tier);
        g.setIntegrated(integrated);
        return g;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Laptop laptop(SubBrand subBrand, Cpu cpu, Gpu gpu, String model, String price,
                          String maxDiscount, int stock, int ram, String ramType, int storage,
                          String storageType, String inches, String displayType, int refresh,
                          boolean touch, String weight, int battery, String os, int year,
                          Map<String, Object> extraSpecs) {
        Laptop l = new Laptop();
        l.setSubBrand(subBrand);
        l.setCpu(cpu);
        l.setGpu(gpu);
        l.setModelName(model);
        l.setBasePrice(new BigDecimal(price));
        l.setMaxDiscountPct(new BigDecimal(maxDiscount));
        l.setStockQty(stock);
        l.setRamGb(ram);
        l.setRamType(ramType);
        l.setStorageGb(storage);
        l.setStorageType(storageType);
        l.setDisplayInches(new BigDecimal(inches));
        l.setDisplayType(displayType);
        l.setRefreshRateHz(refresh);
        l.setTouchscreen(touch);
        l.setWeightKg(new BigDecimal(weight));
        l.setBatteryHours(battery);
        l.setOs(os);
        l.setReleaseYear(year);
        l.setExtraSpecs(extraSpecs);
        return l;
    }

    private Smartphone phone(SubBrand subBrand, Cpu cpu, String model, String price, String maxDiscount,
                             int stock, int ram, int storage, String inches, int refresh, int battery,
                             int camera, String os, int year, Map<String, Object> extraSpecs) {
        Smartphone s = new Smartphone();
        s.setSubBrand(subBrand);
        s.setCpu(cpu);
        s.setModelName(model);
        s.setBasePrice(new BigDecimal(price));
        s.setMaxDiscountPct(new BigDecimal(maxDiscount));
        s.setStockQty(stock);
        s.setRamGb(ram);
        s.setStorageGb(storage);
        s.setDisplayInches(new BigDecimal(inches));
        s.setRefreshRateHz(refresh);
        s.setBatteryMah(battery);
        s.setMainCameraMp(camera);
        s.setOs(os);
        s.setReleaseYear(year);
        s.setExtraSpecs(extraSpecs);
        return s;
    }

    private Map<String, Object> specs(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
