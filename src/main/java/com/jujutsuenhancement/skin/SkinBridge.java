package com.jujutsuenhancement.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Reflection bridge to one of the skin mods (jjkskins, jjkskins15, jjkskinskk, jjkskinsm1: all share the same classes under
 * com.&lt;modid&gt;.client). The skin mods stay untouched; the add-on only reads their skin choice, their geometry (bones) and their textures.
 */
final class SkinBridge {
    static final String[] IDS = {"jjkskins", "jjkskins15", "jjkskinskk", "jjkskinsm1"};
    private static List<SkinBridge> all;

    final String id;
    final String display;
    private final Method get, remove, set, texture, model, stSkin, stBedrock, mReset, mBone, mRender, mTranslate, openScreen;
    private final Field fx, fy, fz, frx, fry, frz, fbx, fby, fbz, fbrx, fbry, fbrz;

    private SkinBridge(String id, String display) throws Exception {
        this.id = id;
        this.display = display;
        Class<?> cs = Class.forName("com." + id + ".client.ClientSkins");
        Class<?> st = Class.forName("com." + id + ".client.ClientSkins$State");
        Class<?> bm = Class.forName("com." + id + ".client.BedrockModel");
        Class<?> bone = Class.forName("com." + id + ".client.BedrockModel$Bone");
        get = cs.getMethod("get", int.class);
        remove = cs.getMethod("remove", int.class);
        set = cs.getMethod("set", int.class, String.class, boolean.class);
        texture = cs.getMethod("texture", String.class);
        model = cs.getMethod("model", st);
        stSkin = st.getMethod("skin");
        stBedrock = st.getMethod("bedrock");
        mReset = bm.getMethod("reset");
        mBone = bm.getMethod("bone", String.class);
        mRender = bm.getMethod("render", PoseStack.class, VertexConsumer.class, int.class, int.class, float.class, float.class, float.class, float.class);
        Method tr = null;
        try {
            tr = bm.getMethod("translateTo", bone, PoseStack.class);
        } catch (Exception ignored) {
            // an older skin mod: no glow layers on its head then
        }
        mTranslate = tr;
        Method open = null;
        try {
            open = Class.forName("com." + id + ".client.ClientHooks").getMethod("openScreen", int.class);
        } catch (Exception ignored) {
            // no screen then
        }
        openScreen = open;
        fx = f(bone, "x"); fy = f(bone, "y"); fz = f(bone, "z");
        frx = f(bone, "rx"); fry = f(bone, "ry"); frz = f(bone, "rz");
        fbx = f(bone, "bx"); fby = f(bone, "by"); fbz = f(bone, "bz");
        fbrx = f(bone, "brx"); fbry = f(bone, "bry"); fbrz = f(bone, "brz");
    }

    private static Field f(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    static synchronized List<SkinBridge> all() {
        if (all == null) {
            all = new ArrayList<>();
            for (String id : IDS) {
                if (!ModList.get().isLoaded(id)) continue;
                try {
                    String name = ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id);
                    all.add(new SkinBridge(id, name));
                } catch (Throwable t) {
                    LogUtils.getLogger().warn("[skin add-on] cannot use skin mod {}: {}", id, t.toString());
                }
            }
        }
        return all;
    }

    /** The skin state of an entity (ClientSkins.State) or null. */
    Object state(int entityId) {
        try {
            return get.invoke(null, entityId);
        } catch (Exception e) {
            return null;
        }
    }

    String skinOf(Object state) {
        try {
            return (String) stSkin.invoke(state);
        } catch (Exception e) {
            return null;
        }
    }

    boolean bedrockOf(Object state) {
        try {
            return (Boolean) stBedrock.invoke(state);
        } catch (Exception e) {
            return true;
        }
    }

    void removeState(int entityId) {
        try {
            remove.invoke(null, entityId);
        } catch (Exception ignored) {
            // nothing
        }
    }

    void setState(int entityId, String skin, boolean bedrock) {
        try {
            set.invoke(null, entityId, skin, bedrock);
        } catch (Exception ignored) {
            // nothing
        }
    }

    ResourceLocation texture(String skin) {
        try {
            return (ResourceLocation) texture.invoke(null, skin);
        } catch (Exception e) {
            return null;
        }
    }

    Object model(Object state) {
        try {
            return model.invoke(null, state);
        } catch (Exception e) {
            return null;
        }
    }

    /** Moves the pose stack to the bone (pivot and rotation of the bone and of its parents). */
    void translateTo(Object bm, Object bone, PoseStack ps) {
        if (mTranslate == null) return;
        try {
            mTranslate.invoke(bm, bone, ps);
        } catch (Exception ignored) {
            // nothing
        }
    }

    void reset(Object bm) {
        try {
            mReset.invoke(bm);
        } catch (Exception ignored) {
            // nothing
        }
    }

    Object bone(Object bm, String name) {
        try {
            return mBone.invoke(bm, name);
        } catch (Exception e) {
            return null;
        }
    }

    void render(Object bm, PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        try {
            mRender.invoke(bm, ps, vc, light, overlay, r, g, b, a);
        } catch (Exception ignored) {
            // nothing
        }
    }

    boolean openScreen(int entityId) {
        if (openScreen == null) return false;
        try {
            openScreen.invoke(null, entityId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Adds {@code d} to the bone's rotation / position offsets (pose deltas of the vanilla part, as the skin mods themselves do). */
    void addDelta(Object bone, float dx, float dy, float dz, float drx, float dry, float drz) {
        try {
            fx.setFloat(bone, fx.getFloat(bone) + dx);
            fy.setFloat(bone, fy.getFloat(bone) + dy);
            fz.setFloat(bone, fz.getFloat(bone) + dz);
            frx.setFloat(bone, frx.getFloat(bone) + drx);
            fry.setFloat(bone, fry.getFloat(bone) + dry);
            frz.setFloat(bone, frz.getFloat(bone) + drz);
        } catch (Exception ignored) {
            // nothing
        }
    }

    /** Sets a bone from a Bedrock animation pose: rotation (radians, added to the geometry's) and position offset (pixels). */
    void setFromAnim(Object bone, float[] rotRad, float[] pos) {
        try {
            if (rotRad != null) {
                frx.setFloat(bone, fbrx.getFloat(bone) + rotRad[0]);
                fry.setFloat(bone, fbry.getFloat(bone) + rotRad[1]);
                frz.setFloat(bone, fbrz.getFloat(bone) + rotRad[2]);
            }
            if (pos != null) {
                fx.setFloat(bone, fbx.getFloat(bone) + pos[0]);
                fy.setFloat(bone, fby.getFloat(bone) - pos[1]);
                fz.setFloat(bone, fbz.getFloat(bone) + pos[2]);
            }
        } catch (Exception ignored) {
            // nothing
        }
    }
}
