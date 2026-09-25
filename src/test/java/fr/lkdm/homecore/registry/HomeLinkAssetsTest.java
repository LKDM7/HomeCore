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
        assertEquals("HomeCore", en.get("itemGroup.homecore.homecore").getAsString());
        assertEquals("HomeCore", fr.get("itemGroup.homecore.homecore").getAsString());
    }

    @Test
    void voxelModelsReferenceReadableSixteenPixelTextures() throws IOException {
        for (String name : new String[] {"homelink_circuit_board", "homelink_microprocessor"}) {
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
