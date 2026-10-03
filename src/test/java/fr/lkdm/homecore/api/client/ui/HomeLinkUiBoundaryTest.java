package fr.lkdm.homecore.api.client.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Checks compiled dependencies without loading client rendering classes. */
class HomeLinkUiBoundaryTest {
    private static final String UI_PACKAGE = "fr/lkdm/homecore/api/client/ui/";

    @Test
    void commonClassesNeverReferenceTheClientUiPackage() throws IOException {
        var classes = mainClasses();
        assertTrue(classes.containsKey("fr/lkdm/homecore/HomeCore.class"), "Main mod bytecode was not scanned");
        assertTrue(classes.containsKey("fr/lkdm/homecore/internal/ServerRuntime.class"), "Server bytecode was not scanned");
        for (var entry : classes.entrySet()) {
            String name = entry.getKey();
            // These existing viewer integrations are client plugins. Common compat
            // utilities remain covered, as do every server/internal/workbench class.
            boolean client = name.contains("/client/")
                    || name.startsWith("fr/lkdm/homecore/compat/jei/")
                    || name.startsWith("fr/lkdm/homecore/compat/rei/");
            if (!client) {
                assertFalse(entry.getValue().stream().anyMatch(value -> value.replace('.', '/').contains(UI_PACKAGE)),
                        "Common class references the client UI kit: " + name);
            }
        }
    }

    @Test
    void pureUiContractsDoNotAcquireMinecraftClientDependencies() throws IOException {
        var classes = mainClasses();
        for (String type : List.of("HomeLinkTheme", "HomeLinkStatusTone", "HomeLinkScreenLayout")) {
            String name = UI_PACKAGE + type + ".class";
            assertTrue(classes.containsKey(name), "Missing compiled UI contract: " + name);
            assertFalse(classes.get(name).stream().anyMatch(value -> value.contains("net/minecraft/client/")),
                    "Pure UI contract references a Minecraft client type: " + type);
        }
    }

    private static Map<String, List<String>> mainClasses() throws IOException {
        Path output = Path.of(System.getProperty("homecore.mainClasses"));
        assertTrue(Files.isDirectory(output), "Main compile output does not exist: " + output.toAbsolutePath());
        Map<String, List<String>> classes = new TreeMap<>();
        try (var paths = Files.walk(output.resolve("fr/lkdm/homecore"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".class")).toList()) {
                classes.put(output.relativize(path).toString().replace('\\', '/'), utf8Constants(path));
            }
        }
        return classes;
    }

    private static List<String> utf8Constants(Path path) throws IOException {
        List<String> constants = new ArrayList<>();
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != 0xCAFEBABE) throw new IOException("Invalid class file: " + path);
            input.readUnsignedShort(); // minor version
            input.readUnsignedShort(); // major version
            int count = input.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> constants.add(input.readUTF());
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unsupported constant pool tag " + tag + " in " + path);
                }
            }
        }
        // Includes class names, method/field descriptors, generic signatures and
        // reflective names; source comments and local variable names cannot create
        // a dependency unless they themselves contain a fully qualified UI name.
        return constants;
    }
}
