# Hexcessible — Forge 1.20.1 port

Forge 1.20.1 port of [hexcessible](https://g.tizu.dev/hexcessible) (Fabric), a
client-side accessibility addon for **Hex Casting**.

Original Fabric source: `hexcessible-mc-1.20.1` (mod version 0.3.1)
Hex Casting reference source: `HexMod-main` (0.11.4)

The entire modification and porting work was done by DeepSeek-V4-Flash

## License

This is a source-derived port of [hexcessible](https://g.tizu.dev/hexcessible)
by Ruby (`mods@tizu.dev`). The ported code retains the upstream license —
the [JSON License](LICENSE) (MIT variant with the "Good, not Evil" clause).

## Features added on top of the port (0.3.1f1)

Three accessibility features were added to the spellcasting (pattern
drawing) interface:

1. **Rebindable shortcuts.** Every Hexcessible shortcut is a named action
   (relative draw directions, absolute draw directions, undo, confirm/cast,
   move, rotate, mode toggle, floating book, autocomplete, alias, autocomplete
   scrolling). Bindings are edited in the **keybindings screen**, opened from
   the button in the **bottom-right corner of the spellcasting interface**
   while drawing. Click a row, press the new key (Delete = restore default,
   Esc = cancel). Letter shortcuts are layout-aware (they match the typed
   character); everything else matches physical key + modifier state, like
   vanilla keybinds. Persisted in the hexcessible config (`keybinds` map;
   see `dev.tizu.hexcessible.keybinds.KeyBinds`). Fixed fallback triggers
   (arrow keys for moving the drawing, Tab/Num-Enter/Space for confirming,
   Backspace for undoing, F2 for aliasing) are kept.
2. **Floating Hex Book.** The "docs" key (N by default, rebindable) no longer
   swaps the game to Patchouli's fullscreen book screen. Instead the Patchouli
   hex book is shown **floating over the drawing interface** as a draggable
   window: fully interactive (flip pages, follow links, search, bookmarks),
   while the pattern grid around it keeps working. Drag it by its title strip,
   close it with the ×, Esc, or the docs key. Exiting the drawing UI while the
   book is open hides it; starting to draw again brings it back **still open,
   at the remembered screen position and page** (also persisted across game
   restarts). Implementation notes:
   * Patchouli navigation funnels through
     `BookContents#openLexiconGui`; while the overlay is active the
     `BookContentsNavMixin` reroutes it into the overlay instead of
     `Minecraft.setScreen`, and the previous book GUI is pushed onto
     Patchouli's own `guiStack`, so Back/links/history behave exactly like
     in the real book (including the page state Patchouli itself remembers).
   * Each frame the book GUI is rendered into an off-screen
     `RenderTarget` the size of the window (via the `BookOverlay`
     controller) and only the region around the pages is copied back at the
     floating position, so mouse coordinates map 1:1 onto the book's own
     coordinate system.
3. **Absolute direction drawing mode.** Upstream keyboard drawing is
   relative: each key is a turn relative to the previous stroke. In the new
   absolute mode every key means one **fixed screen direction** of the next
   stroke: `w` up-left, `e` up-right, `q` left, `a` down-left, `s` down-right,
   `d` right (all rebindable). Pressing the key that points back at the
   previously drawn spot **undoes the last stroke** (like mouse backtracking;
   Backspace also works). The mode toggle key (`` ` `` by default, shown in
   the bottom-left hints) switches between relative and absolute; the last
   mode is remembered (config `keyboardDraw.absoluteMode`), and toggling is
   allowed whenever nothing has been typed yet.

## Port notes

* Target: **Forge 1.20.1 (47.1.47)**, Java 17, official (Mojmap) mappings.
* Depends on **Hex Casting Forge 0.11.4** (`at.petra-k.hexcasting:hexcasting-forge-1.20.1`),
  the published build of the same source tree in `../HexMod-main`. The Fabric
  original targets `0.11.2-pre-702`; the APIs used here were verified against
  the compiled `0.11.4` Forge jar (`GuiSpellcasting`, `RenderLib`,
  `ClientRenderHelper`, `ResolvedPattern`, `MsgNewSpellPatternC2S`, …).
* Patchouli Forge `1.20.1-84-FORGE` is used for the hex book integration
  (`BookEntries`, and the floating book overlay described above — a
  `BookContents` mixin reroutes Patchouli navigation into a draggable
  overlay window while the casting UI is open).
* Config uses Cloth Config's **AutoConfig** (identical to the Fabric build);
  the ModMenu entry point is replaced with a Forge
  `ConfigScreenHandler.CONFIG_SCREEN_FACTORY` extension point
  (`HexcessibleForge`), which adds a "Config" button to the mods list.
* `FabricLoader.getInstance().isModLoaded(...)` became
  `ModList.get().isLoaded(...)` in the SmartSig conditionals
  (ComplexHex / Hexical / Hex Things / Overevaluate). These mods are
  Fabric-only today, so those SmartSigs stay disabled on Forge, matching the
  original behavior when the mods are absent.
* Yarn→Mojmap translation applied across the codebase:
  `MinecraftClient`→`Minecraft`, `DrawContext`→`GuiGraphics`,
  `Text`→`Component`, `Formatting`→`ChatFormatting`, `Identifier`→`ResourceLocation`,
  `Vec2f`→`Vec2`, `Hand`→`InteractionHand`, `KeyBinding`→`KeyMapping`,
  `ParentElement`→`ContainerEventHandler`, `PlayerEntity`→`Player`,
  `MathHelper`→`Mth`, `JsonHelper`→`GsonHelper`, `WorldSavePath`→`LevelResource`,
  `Screen.close()`→`Screen.onClose()`, `Registry.getKeys()/get()/getEntry()`
  →`keySet()/getValue()/getHolder()`, etc.
* Mixins are loaded the Forge way: config `hexcessible.mixins.json` is shipped
  via the `MixinConfigs` manifest attribute (added by MixinGradle from the
  `mixin { config ... }` block), with a refmap generated against official
  mappings. `@WrapMethod` (MixinExtras) works out of the box because Forge
  47.1.x bundles MixinExtras.
* **Removed from the port**: the two HexDebug interop mixins
  (`DrawStateHexdbgInteropMixin`, `DrawStateHexdbgInteropParentElemMixin`) —
  HexDebug (`gay.object.hexdebug`) publishes no Forge build for 1.20.1, and
  loading a mixin whose target class can never exist would crash the game.
* The three MixinExtras `@WrapMethod`s of the Fabric original
  (`drawStart`, `renderCastingStack`, `makeZappy`) were rewritten as plain
  cancellable `@Inject`s, because Forge 47.1.x bundles MixinExtras 0.3.5 which
  predates `@WrapMethod` (added in 0.4.0). The behavior is identical.
* `GuiSpellcasting#drawEnd` is invoked **reflectively** via a private
  `MethodHandle` in `CastingInterfaceAccessor#stopDrawing` instead of the
  Fabric original's `@Shadow(prefix = "hexcessible$")` method: on Forge that
  shadow doubles as a `DrawStateMixinAccessor` interface implementation, so
  the Mixin runtime injects an *abstract* `hexcessible$drawEnd` into
  `GuiSpellcasting` and the first call crashes with `AbstractMethodError`
  (reported from autocomplete: `AutoCompleting.onCharType` →
  `CastRef.stopDrawing`).
* `GuiSpellcasting#mouseMoved` is targeted with `@Inject(method = "m_94757_",
  remap = false)` plus a **refmap patch**: the Mixin annotation processor
  cannot map `mouseMoved` (the MCP mapping data only lists it under
  `GuiEventListener`, while the inject targets the Kotlin override in
  `GuiSpellcasting`), so `build.gradle`'s `patchRefmap` task adds the
  `mouseMoved -> GuiSpellcasting;m_94757_(DD)V` entry to the generated refmap.
  The SRG name is stable for 1.20.1.
* `charTyped` interception moved to `KeyboardHandler#charTyped`: the Fabric
  original injected into `ContainerEventHandler` (yarn's `ParentElement`) with
  an interface mixin, but the Mixin annotation processor hard-rejects
  injectors declared in interface mixins, and Mojmap `Screen` does not declare
  `charTyped` (it inherits the interface default), so an interface or
  `Screen`-targeted inject cannot work on Forge. Intercepting at the input
  source is behaviorally equivalent for the spellcasting screen.
* The generated refmap is copied into `build/resources/main` (via
  `processResources`) so dev runs (`runClient`/`runServer`) can remap it —
  MixinGradle only puts it in the produced jar otherwise.
* A `pack.mcmeta` (pack_format 15) is shipped: without it Forge logs
  "Missing metadata in pack mod:hexcessible" and the mod's assets — including
  the language files — are not loaded, so every translation key shows up
  verbatim in the UI (e.g. `hexcessible.hint.auto_complete` instead of
  "autocomplete").
* `zh_cn.json` was re-fetched from the upstream repo
  (github.com/tizu69/hexcessible): the copy inside the Fabric source archive
  was mangled (UTF-8 re-encoded through GBK) and would render as garbage once
  the pack loads. `en_us.json` and the icon are byte-for-byte from the archive.

## Building

Requirements: **JDK 17** (ForgeGradle 6 / Gradle 8.12.1 do not support newer
JDKs) and network access for the first build.

First check your Java version:

```bat
java -version
```

If it does not report 17, point the build at your JDK 17 installation. The
path differs per machine — replace it with your own:

```bat
REM cmd
set JAVA_HOME=D:\path\to\your\jdk17
gradlew.bat build
```

```powershell
# PowerShell
$env:JAVA_HOME='D:\path\to\your\jdk17'
.\gradlew.bat build
```

If `java -version` already reports 17 (or `JAVA_HOME` already points at a
JDK 17), you can run `gradlew.bat build` directly.

The built jar lands in `build/libs/hexcessible-forge-0.3.1f1.jar`. To run a
dev client:

```bat
gradlew.bat runClient
```

## Verified

* `gradlew build` succeeds (ForgeGradle 6.0.54, Gradle 8.12.1, JDK 17) and
  produces the reobfuscated jar with `MixinConfigs: hexcessible.mixins.json`
  in the manifest and a complete SRG refmap (including the patched
  `mouseMoved` entry).
* `gradlew runClient` boots Forge 1.20.1 (47.1.47) with Hex Casting 0.11.4
  Forge + Patchouli + Cloth Config to the title screen; the dev client applies
  all startup-relevant hexcessible mixins cleanly, including
  `DrawStateMixin`/`DimmedMixin`/`PerWorldLearnMixin`/`ShowAllDotsMixin` into
  `GuiSpellcasting`, the `Screen`/`KeyMapping`/`KeyboardHandler` mixins and
  `RenderLibMixin` (`FloatiesMixin` targets `ClientRenderHelper`, which loads
  lazily while casting).
* In-game interaction (keyboard drawing, autocomplete, ...) requires a
  play session; the drawstate logic itself is unchanged from the Fabric
  original.

## Known incompatibility: ImmediatelyFast on 1.20.1 (auto-mitigated)

ImmediatelyFast's 1.20.x builds (e.g. `ImmediatelyFast-Forge-1.5.5+1.20.4.jar`,
which declare support for `[1.20,1.20.4]` and are the only 1.20.1 builds IF
publishes) render the hexcessible autocomplete text as boxes/tofu on 1.20.1.
Bisection shows the culprit is IF's `font_atlas_resizing` feature (glyph atlas
256→2048): its mixin targets the 1.20.4 glyph-atlas implementation and is not
fully compatible with the 1.20.1 one when many glyphs are uploaded dynamically,
which the autocomplete panel does every frame.

Hexcessible mitigates this automatically: `ImmediatelyFastCompat` detects IF
during `FMLClientSetupEvent` (before any glyph atlas exists) and flips IF's
runtime `font_atlas_resizing` flag to false via reflection, so every atlas is
created at 256×256. No user configuration is needed; the startup log notes
"ImmediatelyFast detected: disabled its font_atlas_resizing feature...".
The mitigation is soft-failing and only touches IF's public runtime-config
fields. This is an IF/1.20.1 compatibility bug, not a hexcessible one —
hexcessible only uses standard `GuiGraphics`/`Font` calls.

## Runtime dependencies (all from the Fabric build, Forge variants)

| Mod | Version |
| --- | --- |
| Hex Casting | 0.11.4 (forge) |
| Patchouli | 1.20.1-84-FORGE |
| Cloth Config | 11.1.118 (forge) |
| Paucal | 0.6.0-pre-118 (forge, required by Hex Casting) |
| Inline | 1.20.1-1.2.2 (forge, required by Hex Casting) |
| Caelus | 3.1.0+1.20 (forge, required by Hex Casting) |
| Kotlin for Forge | 4.12.0 (required by Hex Casting) |

## Structure

```
src/main/java/dev/tizu/hexcessible/
├── Hexcessible.java            – constants/logging (unchanged)
├── HexcessibleConfig.java      – AutoConfig (+ new features' persisted state)
├── HexcessibleForge.java       – Forge @Mod entrypoint + config screen (new)
├── Utils.java                  – ported (+ angle-from-direction helpers)
├── accessor/                   – CastRef, CastingInterfaceAccessor (reflection), DrawStateMixinAccessor
├── drawstate/                  – Idling, MouseDrawing, KeyboardDrawing (relative+absolute modes), AutoCompleting, AliasChanging, DrawState
├── entries/                    – PatternEntries, BookEntries
├── gui/                        – BookOverlay (floating book), KeyConfigScreen (new)
├── keybinds/                   – KeyBinds: the rebindable action registry (new)
├── smartsig/                   – Number, Bookkeeper, Escape, Hexical/HexThings/Overevaluate/ComplexHex conditionals
└── mixin/                      – 11 client mixins (+ BookContentsNavMixin, KeyDocsScreenMixin removed)
```
