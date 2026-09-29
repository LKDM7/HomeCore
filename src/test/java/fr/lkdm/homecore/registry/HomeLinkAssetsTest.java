package fr.lkdm.homecore.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomeLinkAssetsTest {
    private static final String[] COMPONENTS = {"homelink_circuit_board", "homelink_microprocessor",
            "homelink_communication_module", "homelink_control_module"};

    @Test
    void namesAndTooltipsAreTranslatedInBothLanguages() throws IOException {
        JsonObject en = json("assets/homecore/lang/en_us.json");
        JsonObject fr = json("assets/homecore/lang/fr_fr.json");
        assertTranslation(en, "homelink_circuit_board", "HomeLink Circuit Board",
                "Basic electronic component used by HomeLink devices.");
        assertTranslation(fr, "homelink_circuit_board", "Circuit imprimé HomeLink",
                "Composant électronique de base utilisé par les appareils HomeLink.");
        assertTranslation(en, "homelink_microprocessor", "HomeLink Microprocessor",
                "Compact processor used by advanced HomeLink systems.");
        assertTranslation(fr, "homelink_microprocessor", "Microprocesseur HomeLink",
                "Processeur compact utilisé par les systèmes HomeLink avancés.");
        assertTranslation(en, "homelink_communication_module", "HomeLink Communication Module",
                "Communication and data transmission module for HomeLink devices.");
        assertTranslation(fr, "homelink_communication_module", "Module de communication HomeLink",
                "Module de transmission et de communication pour les appareils HomeLink.");
        assertTranslation(en, "homelink_control_module", "HomeLink Control Module",
                "Control module used by automated HomeLink machines.");
        assertTranslation(fr, "homelink_control_module", "Module de contrôle HomeLink",
                "Module de contrôle utilisé par les machines automatisées HomeLink.");
        assertEquals("HomeCore", en.get("itemGroup.homecore.homecore").getAsString());
        assertEquals("HomeCore", fr.get("itemGroup.homecore.homecore").getAsString());
    }

    @Test
    void voxelModelsReferenceReadableSixteenPixelTextures() throws IOException {
        for (String name : COMPONENTS) {
            JsonObject model = json("assets/homecore/models/item/" + name + ".json");
            assertEquals("minecraft:block/block", model.get("parent").getAsString());
            assertTrue(model.getAsJsonArray("elements").size() >= 2);
            assertEquals("homecore:item/" + name,
                    model.getAsJsonObject("textures").get("face").getAsString());
            assertEquals("homecore:item/homelink_model_palette",
                    model.getAsJsonObject("textures").get("palette").getAsString());
            assertTrue(model.getAsJsonObject("display").has("gui"));
            try (InputStream stream = resource("assets/homecore/textures/item/" + name + ".png")) {
                BufferedImage image = ImageIO.read(stream);
                assertNotNull(image, name + " PNG could not be decoded");
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                assertTrue(image.getColorModel().hasAlpha());
                assertTrue(image.getRGB(8, 8) >>> 24 != 0, name + " has no visible center");
            }
        }
        try (InputStream stream = resource("assets/homecore/textures/item/homelink_model_palette.png")) {
            BufferedImage palette = ImageIO.read(stream);
            assertNotNull(palette);
            assertEquals(16, palette.getWidth());
            assertEquals(16, palette.getHeight());
        }
    }

    private static void assertTranslation(JsonObject language, String name, String title, String tooltip) {
        assertEquals(title, language.get("item.homecore." + name).getAsString());
        assertEquals(tooltip, language.get("item.homecore." + name + ".tooltip").getAsString());
    }

    @Test
    void workbenchStatesResolveModelsAndSixteenPixelTextures() throws IOException {
        JsonObject variants = json("assets/homecore/blockstates/electronics_workbench.json")
                .getAsJsonObject("variants");
        assertEquals(48, variants.size());
        for (String facing : new String[]{"north", "east", "south", "west"}) {
          for (String part : new String[]{"single", "left", "right"}) {
            for (int stage = 0; stage < 4; stage++) {
                String modelId = variants.getAsJsonObject("facing=" + facing + ",part=" + part + ",stage=" + stage)
                        .get("model").getAsString();
                JsonObject model = json("assets/" + modelId.replace(":", "/models/") + ".json");
                for (var entry : model.getAsJsonObject("textures").entrySet()) {
                    String textureId = entry.getValue().getAsString();
                    try (InputStream stream = resource("assets/" + textureId.replace(":", "/textures/") + ".png")) {
                        BufferedImage image = ImageIO.read(stream);
                        assertNotNull(image, textureId);
                        assertEquals(16, image.getWidth(), textureId);
                        assertEquals(16, image.getHeight(), textureId);
                    }
                }
            }
          }
        }
        JsonObject itemModel = json("assets/homecore/models/item/electronics_workbench.json");
        double minimumX = 32, maximumX = -16;
        for (var element : itemModel.getAsJsonArray("elements")) {
            JsonObject cube = element.getAsJsonObject();
            minimumX = Math.min(minimumX, cube.getAsJsonArray("from").get(0).getAsDouble());
            maximumX = Math.max(maximumX, cube.getAsJsonArray("to").get(0).getAsDouble());
        }
        assertEquals(32, maximumX - minimumX, "Inventory model must show both halves");
        assertTrue(minimumX >= -16 && maximumX <= 32);
    }

    @Test
    void workbenchAndRecipePartsHaveMatchingEnglishAndFrenchLabels() throws IOException {
        JsonObject en = json("assets/homecore/lang/en_us.json");
        JsonObject fr = json("assets/homecore/lang/fr_fr.json");
        assertEquals(en.keySet(), fr.keySet(), "Locales must expose the same translated labels");
        assertEquals("HomeLink Electronics Workbench", en.get("block.homecore.electronics_workbench").getAsString());
        assertEquals("\u00c9tabli \u00e9lectronique HomeLink", fr.get("block.homecore.electronics_workbench").getAsString());
        for (String name : COMPONENTS) {
            JsonObject recipe = json("data/homecore/recipe/" + name + ".json");
            assertEquals("homecore:electronics", recipe.get("type").getAsString());
            for (var element : recipe.getAsJsonArray("assembly_layout")) {
                String key = element.getAsJsonObject().get("label").getAsString();
                assertTrue(en.has(key), "Missing English label " + key);
                assertTrue(fr.has(key), "Missing French label " + key);
            }
        }
    }

    @Test
    void workbenchLootDropsOneItemFromTheOwningHalfOnly() throws IOException {
        JsonObject loot = json("data/homecore/loot_table/blocks/electronics_workbench.json");
        var conditions = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions");
        assertEquals("minecraft:survives_explosion", conditions.get(0).getAsJsonObject().get("condition").getAsString());
        JsonObject ownership = conditions.get(1).getAsJsonObject();
        assertEquals("minecraft:any_of", ownership.get("condition").getAsString());
        var owners = new java.util.HashSet<String>();
        for (var element : ownership.getAsJsonArray("terms")) {
            JsonObject term = element.getAsJsonObject();
            assertEquals("minecraft:block_state_property", term.get("condition").getAsString());
            assertEquals("homecore:electronics_workbench", term.get("block").getAsString());
            owners.add(term.getAsJsonObject("properties").get("part").getAsString());
        }
        assertEquals(java.util.Set.of("single", "left"), owners);
    }

    @Test
    void connectorHasCraftingRecipeModelAndEveryBindingOutcomeTranslation() throws IOException {
        JsonObject recipe = json("data/homecore/recipe/homelink_connector.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("homecore:homelink_connector", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals("homecore:homelink_communication_module",
                recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        JsonObject model = json("assets/homecore/models/item/homelink_connector.json");
        String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
        try (InputStream stream = resource("assets/" + texture.replace(":", "/textures/") + ".png")) {
            assertNotNull(ImageIO.read(stream));
        }
        for (String language : new String[]{"en_us", "fr_fr"}) {
            JsonObject labels = json("assets/homecore/lang/" + language + ".json");
            assertTrue(labels.has("item.homecore.homelink_connector"));
            assertTrue(labels.has("item.homecore.homelink_connector.tooltip"));
            for (var result : fr.lkdm.homecore.api.network.NetworkMember.BindResult.values()) {
                String key = "message.homecore.connector." + result.name().toLowerCase(java.util.Locale.ROOT);
                assertTrue(labels.has(key), "Missing " + language + " outcome " + key);
            }
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = HomeLinkAssetsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing resource: " + path);
        return stream;
    }
}
