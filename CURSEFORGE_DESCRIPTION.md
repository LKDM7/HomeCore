# HomeCore

**Short description**

A shared device API and electronics crafting foundation for the HomeLink mod ecosystem.

**Project description**

HomeCore is the common foundation for mods that add connected devices and smart systems to your Minecraft world. It provides a shared API for device data, metrics, actions, events, permissions and persistent networks, so compatible mods can work together without depending on one another.

HomeCore also adds four shared crafting components and the **HomeLink Electronics Workbench**, where they are assembled:

- **HomeLink Circuit Board** — a basic electronic component crafted from vanilla Copper, Redstone and Quartz. One assembly produces two boards.
- **HomeLink Microprocessor** — an advanced component crafted from a Circuit Board and vanilla Gold Nuggets, Copper, Redstone and Quartz.
- **HomeLink Communication Module** — communication, networking and sensing, crafted from a Circuit Board, a Microprocessor and vanilla Copper, Redstone, Quartz and an Amethyst Shard.
- **HomeLink Control Module** — machine control and automation, crafted from a Circuit Board, a Microprocessor, a Comparator and vanilla Copper, Redstone and Iron.
- **Electronics Workbench** — a two-block workstation with a shared inventory and an interactive assembly interface. Choose a batch of up to 64 finished items, arrange one prototype, then collect the completed batch. It requires no power or fuel.

Other HomeLink mods can add their own workbench recipes through HomeCore's extensible recipe system. For example, a farm or storage add-on can use the shared components without requiring the other add-on. HomeCore does not include those add-on machines; install the HomeLink mods you want to use alongside it.

The **HomeLink Connector** links compatible devices to a shared HomeNetwork. Select a network by using the tool in the air, then use it on a device. Machine ownership and network permissions are checked on the server. HomeCore also provides shared directional item ports for compatible automation mods.

The workbench can be crafted with vanilla materials:

```text
I R I    I = Iron Ingot       R = Redstone
C W C    C = Copper Ingot     W = Crafting Table
P P P    P = Any Planks
```

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.250 or a compatible 21.1 release
- Java 21

Install HomeCore on the client and server. HomeCore does not require HomeLink Farm, Storage, Dashboard or any other add-on. Mods that use the HomeCore API can list it as a required dependency.
