# Electronics Workbench recipes

HomeLink components are assembled exclusively at the HomeLink Electronics Workbench.
The workbench discovers recipes from Minecraft's recipe manager. An add-on supplies
`data/<namespace>/recipe/<name>.json` with `type` set to `homecore:electronics`.
No Java registration or dependency on another HomeLink add-on is needed.

See the [Circuit Board](../src/main/resources/data/homecore/recipe/homelink_circuit_board.json),
[Microprocessor](../src/main/resources/data/homecore/recipe/homelink_microprocessor.json),
[Communication Module](../src/main/resources/data/homecore/recipe/homelink_communication_module.json)
and [Control Module](../src/main/resources/data/homecore/recipe/homelink_control_module.json)
recipes for complete examples.

`ingredients` contains 1–9 objects with `ingredient` (a Minecraft item/tag ingredient)
and `count` (1–576 units per assembly). NeoForge ingredient codecs are supported.
Overlapping alternatives are allocated together, so a broad ingredient cannot
consume an item still needed by a narrower ingredient.

`result` uses Minecraft 1.21.1's item stack format, for example
`{ "id": "homecore:homelink_circuit_board", "count": 2 }`. Yield is expressed only
as `result.count`, with no separate `result_count` field. The yield must fit one
output stack and cannot exceed 64. Requested quantities are final items, must be
divisible by the yield, and must fit the remaining output capacity. Input consumption
is `ingredient.count × requested final items / result.count`.

`assembly_layout` contains 1–8 draggable material groups. Each has a unique `id`,
a `label` translation key, an `ingredient_index` (zero-based index into `ingredients`),
and target coordinates `x` and `y` from 0 to 100 over the assembly board. Several
parts may share one ingredient, for example two copper connections on either side. Use
separated targets so drop areas remain easy to select. One completed prototype
validates the whole batch, regardless of quantity.

`processing_time` is a base duration in server ticks, from 1 to 80 (default 20).
The workbench scales the batch animation with quantity and caps it at 80 ticks.
`category` is an optional grouping string, defaulting to `components`.

Recipes appear directly in the workbench selector, ordered by the registration
order of their result item. They do not need vanilla Recipe
Book advancements, and are not crafting-table recipes. An add-on should declare an
explicit compatible HomeCore dependency version.

Crafting remainders (for example empty buckets) return to material storage after
successful production; overflow drops at the workbench. Cancelled prototypes
return the original ingredients, without creating remainders.
