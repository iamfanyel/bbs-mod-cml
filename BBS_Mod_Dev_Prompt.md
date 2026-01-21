# BBS Mod Development Master Prompt

You are an expert developer for the **BBS Mod** (Blockbuster Successor), a complex Minecraft mod for Fabric (1.20.x) focused on machinima, filming, and custom forms. Use the following guide to understand the codebase architecture and implement new features.

**IMPORTANT**:
*   **Wiki Location**: `c:\Users\lyanh\Documents\BBS-XYZ NEW\BBS-Xyz\cml_fork\bbs-mod-wiki.wiki`
*   **Rule**: When adding ANY new feature (Form, Clip, Keybind, etc.), **ALWAYS READ THE WIKI FIRST** to understand existing patterns and features.

---

## 1. Core Systems & Knowledge

### **UIKey (Localization)**
The mod uses a strict localization system wrapped in `IKey` interfaces. **Never hardcode strings** in the UI.

*   **Interface**: `mchorse.bbs_mod.l10n.keys.IKey`
*   **Usage**: Define static keys in a `UIKeys` class.
*   **Localization File**: `src/client/resources/assets/bbs/assets/strings/en_us.json`
*   **Implementation**:
    ```java
    // 1. Define Key
    public static final IKey MY_LABEL = L10n.lang("bbs.ui.my_label");
    
    // 2. Use in UI
    button.tooltip(UIKeys.MY_LABEL);
    ```

### **UI Architecture**
The UI is built on a custom framework centered around `UIElement`.
*   **Base Class**: `mchorse.bbs_mod.ui.framework.elements.UIElement`
*   **Common Elements**:
    *   **Button**: `UIButton` (Action triggers).
    *   **Toggle**: `UIToggle` (Boolean switch).
    *   **Color Picker**: `UIColor` (RGB/Alpha selection).
    *   **Icon**: `UIIcon` (Renders an icon).
*   **Layouts**: Use `UI.row(element)` and `UI.column(element)` for flex-like layouts.
*   **Context**: `UIContext` is passed to render methods for font rendering and mouse coordinates.
*   **Panels & Dashboards**:
    *   **UIDashboard**: The main editor interface (`0` key). Manages sub-panels.
    *   **UIPanelBase**: Base class for full-screen UI panels.
    *   **UIOverlayPanel**: Base for modal overlays.

### **BBS Knowledge**
*   **Registration**: Features (Forms, Clips) are registered in `BBSMod` (Common) and client utilities (Client).
*   **Values**: The mod uses a `Value` system (e.g., `ValueFloat`, `ValueBoolean`, `ValueGroup`) to automatically handle serialization (NBT/JSON) and UI syncing.
*   **Model Formats**:
    *   **BBS (.bbs.json)**: Native custom format, supports complex rigs and animations.
    *   **BOBJ (.bobj)**: Binary OBJ format (Blockbuster), optimized for performance.
    *   **GEO (.geo.json)**: Bedrock/GeckoLib geometry format.
    *   **OBJ (.obj)**: Standard Wavefront OBJ (static or shape keys).
    *   **VOX (.vox)**: MagicaVoxel format.

---

## 2. Film System Implementation

### **Form System**
Forms dictate the visual appearance of actors and objects.
*   **Base Class**: `mchorse.bbs_mod.forms.forms.Form`
*   **Format Support**: Handles `.bbs.json` (custom format), `.obj`, and `.bobj` (binary object).
*   **BodyPart System**: Implemented in `BodyPart.java`. Allows attaching child forms to parent bones.
    *   *Extending*: Add `Value` fields to `BodyPart` class to store new attachment properties.
*   **Standard Forms**: Model, Billboard, Label, Extruded, Block, Item, Mob, Particle.

#### **Deep Dive: ModelForm**
The `ModelForm` is the most complex form, handling animated rigs.
*   **Class**: `mchorse.bbs_mod.forms.forms.ModelForm`
*   **Properties**:
    *   `model`: String ID of the model.
    *   `pose`: Base `Pose` transformation (bones).
    *   `poseOverlay`: Secondary `Pose` for animations on top of base pose.
    *   `actions`: `ActionsConfig` mapping abstract actions (e.g., "running") to specific animations.
    *   `shapeKeys`: `ShapeKeys` for OBJ morph targets.
*   **Renderer**: `ModelFormRenderer`
    *   **Pipeline**: `render3D()` -> `ensureAnimator()` -> `animator.applyActions()` -> `model.applyPose()` -> `renderModel()`.
    *   **IAnimator**: Handles `Animator` (standard) or `ProceduralAnimator` (code-driven).
    *   **MatrixCache**: Caches bone matrices for attaching BodyParts or Items.

#### **Deep Dive: Particle Form**
BBS mod supports a subset of Bedrock particle format.
*   **Editors**: Use [Snowstorm](https://snowstorm.app/) or the in-mod editor.
*   **Features**:
    *   `paused` track: Control emission state.
    *   `texture` track: Animated texture changes.
    *   **MoLang Integration**: Supports `variable.emitter_user_1` to `_6` for dynamic math-driven effects in the timeline.

### **Replay System**
Handles the recording and playback of entities.
*   **Class**: `mchorse.bbs_mod.film.replays.Replay`
*   **Structure**: Extends `ValueGroup`. Contains `ReplayKeyframes` for motion data and `FormProperties` for visual animation.
*   **Features**:
    *   **Multi-Replay Editing**: Supports bulk editing of properties and keyframe offsets.
    *   **Onion Skinning**: Ghost overlays for animation reference.
    *   **Actor Mode**: Toggles between virtual replay and physical entity interaction.

### **Keyframe System**
Handles interpolation of values over time.
*   **Core Classes**:
    *   `KeyframeChannel<T>`: Manages a sorted list of Keyframes. Handles binary search (`findSegment`) and logic (`interpolate`).
    *   `Keyframe<T>`: Stores `tick`, `value`, `interp`, and Bezier handles (`lx`, `ly`, `rx`, `ry`).
    *   `Interpolation`: Wraps `IInterp` (Linear, Bezier, Easing) and `EasingArgs` (v1-v4 params).
*   **Factories**: `IKeyframeFactory<T>`
    *   Must implement logic to serialize/deserialize and interpolate the specific type `T`.
    *   Registered in `KeyframeFactories`.
    *   *Example Types*: Float, Double, Integer, Pose, Color, Link.
*   **Extensibility**:
    *   To add a new animatable type, implement `IKeyframeFactory<MyType>`.
    *   Register it: `KeyframeFactories.FACTORIES.put("my_type", new MyFactory())`.
*   **Advanced Features**:
    *   **Floating Point Ticks**: Supports sub-tick precision.
    *   **Texture Animation**: Interpolates `_NUMBER.png` sequences.
    *   **Anchor Track**: Attaches replays to other replays/bones (Translate/Scale modes).

### **Clip System**
Segments on the timeline (Camera paths, events).
*   **Common Clips**:
    *   **Camera**: Path, Keyframe (`distance` track), Dolly, Idle, Look, Orbit, Tracker (attaches to body parts).
    *   **Effects**: Shake, Drag (smooth), Math (expressions).
    *   **Media**: Audio, Subtitle.
    *   **Utility**: Remapper (time warp), Envelope (transitions).
    *   **Curve Clip**: Animates generic values (Sun rotation, Shader options).
    *   **Dolly Zoom**: Vertigo effect via FOV/Distance animation.

---

## 3. Keybind System Architecture

The mod uses a custom keybind system distinct from vanilla Minecraft's `KeyMapping`.

### **Core Components**
*   **`KeyCombo`**: Defines the static combination of keys (Main Key + Modifiers).
    *   *Location*: `mchorse.bbs_mod.ui.utils.keys.KeyCombo`
    *   *Usage*: Define static constants in `Keys.java`.
*   **`Keybind`**: The runtime object linking a `KeyCombo` to a `Runnable` action.
    *   *Location*: `mchorse.bbs_mod.ui.utils.keys.Keybind`
    *   *Validation*: Checks `Window.isKeyPressed()` and modifiers (`Shift`, `Ctrl`, `Alt`).
*   **`KeybindManager`**: Manages the list of active keybinds for a UI context.
    *   *Location*: `mchorse.bbs_mod.ui.utils.keys.KeybindManager`

### **Implementing a New Keybind**
1.  **Define KeyCombo**: Add a static field in `Keys.java`.
    ```java
    public static final KeyCombo MY_ACTION = new KeyCombo(UIKeys.MY_ACTION_LABEL, GLFW.GLFW_KEY_M, GLFW.GLFW_MOD_CONTROL);
    ```
2.  **Register Action**: In your UI panel (e.g., `UIEditorPanel`), create a `Keybind`.
    ```java
    // In UIElement constructor
    this.keys().register(Keys.MY_ACTION, () -> {
        this.performMyAction();
    }).category(Keys.CATEGORY_EDITORS);
    ```
3.  **Global Keybinds**: For global actions (overlay toggles), register in the main client event loop or `Overlay` class.

### **Important Keybinds Reference(Not in the UI)**
*   **General**: `0` (Dashboard), `.` (Demorph), `B` (Morph Menu), `F6` (Utility Panel), `F9` (Keybinds List).
*   **Film Editor**: `Right Shift` (Open Replays), `Right Ctrl` (Play), `Right Alt` (Record), `Y` (Teleport to Replay).
*   **Tools**: `V` (Scale Keyframes), `B` (Stack Keyframes), `[`/`]` (Looping Region).

---

## 4. Implementation Guides

### **How to Create a New UI Element**
1.  **Create Class**: Extend `UIElement`.
2.  **Override Render**: Implement `render(UIContext context)` for drawing.
3.  **Handle Input**: Override `mouseClicked`, `keyPressed`, etc.
    ```java
    public class UICustomButton extends UIElement {
        @Override
        public void render(UIContext context) {
            // Draw background
            context.batcher.box(this.area, 0xFF000000);
            super.render(context); // Render children
        }
    }
    ```

### **How to Create a New Form (with UI)**
1.  **Data Class**: Create `public class MyForm extends Form`. Define properties as `Value` fields.
2.  **Renderer**: Create `public class MyFormRenderer extends FormRenderer<MyForm>`. Implement `render()`.
3.  **UI Panel**: Create `public class UIMyFormPanel extends UIFormPanel<MyForm>`. Add fields to `this.add()`.
4.  **Registration**:
    *   **Common**: `FormArchitect.register("my_form", MyForm.class);`
    *   **Client**: `FormUtilsClient.register(MyForm.class, MyFormRenderer::new);`
    *   **Editor**: `UIFormEditor.register(MyForm.class, UIMyFormPanel::new);`

### **How to Add a New Clip**
1.  **Data Class**: Create `public class MyClip extends Clip`. Define properties.
2.  **Factory**: Implement `create()` to return a new instance.
3.  **UI Editor**: Create `public class UIMyClip extends UIClip<MyClip>`. Populate fields.
4.  **Registration**:
    *   **Common**: `BBSMod.factoryActionClips.register(Link.bbs("my_clip"), MyClip.class, new ClipFactoryData(...));`
    *   **Client**: Add to static block in `UIClip.java`: `register(MyClip.class, UIMyClip::new);`

### **How to Prevent Rendering Bugs (Model System)**
When implementing renderers, follow these rules to ensure compatibility with Iris/Shaders:

1.  **Shadow Pass Check**: Avoid rendering 2D elements or complex transparency during shadow passes.
    ```java
    if (BBSSettings.isIrisShadowPass()) return;
    ```
2.  **State Management**: Always push/pop matrix stack and restore render state.
    ```java
    RenderSystem.enableBlend();
    // ... draw ...
    RenderSystem.disableBlend();
    ```
3.  **Shader Attributes**: Check `BBSRendering.isIrisShadersEnabled()` before using custom vertex attributes (like tangents) that might crash vanilla shaders.
4.  **World Rendering**: Use `BBSRendering.isRenderingWorld()` to determine if you are in the GUI or the World, and adjust lighting accordingly (GUI needs fake lighting).
