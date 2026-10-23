package com.postmalloy.aeroweather.integration.interactivefoliage;

import java.util.regex.Pattern;

/**
 * Rewrites Interactive Foliage's shader text so the direction grass leans comes
 * from the {@code Weather} uniform instead of a hard-coded constant (M16).
 * <p>
 * Pure string functions with no Minecraft or Interactive Foliage types, so the
 * rewrite can be checked offline against the real shader files.
 *
 * <h2>Why {@code Weather.w}</h2>
 * IF's wind direction is a fixed west, {@code vec2(-1.0, 0.0)}, baked into two
 * separate shader sources that reach the GPU three different ways. A new
 * uniform would have to be plumbed through all three, and IF's
 * {@code foliage_legacy.json} only exposes the uniforms it lists, so vanilla's
 * {@code ShaderInstance} couldn't even set one. {@code Weather} already reaches
 * every pipeline, and its fourth component is only ever IF's reach fade, which
 * IF's Java always sets to the constant 4.0. So {@code w} is free to carry an angle,
 * provided the fade uses the literal instead.
 *
 * <h2>The negative sentinel</h2>
 * An angle is sent as {@code w = -(angle + 10)}, which is always below
 * {@link #ENCODED_ANGLE_BELOW}, while IF's fade is always positive. Every patched
 * expression checks the sign: negative means "AeroWeather's wind, a fade of 4",
 * positive means "IF's own direction and this fade", and in that case each expression
 * is IF's original text verbatim. So a patched shader behaves identically to IF's
 * unless it is handed a negative {@code w}, which is what lets {@link FoliageWind} fall
 * back to strength-only at any time without the shader and the uniform ever
 * disagreeing. The offset of 10 only has to exceed pi, so that angle 0 still encodes
 * well below the threshold.
 * <p>
 * Between the threshold and 0 ({@link #OWN_DIRECTION}) means our wind, but blowing the
 * way IF's own direction says. That's for Sable sub-levels (IF 2.2+): IF turns a world
 * vector into each ship's plot frame itself, and {@link FoliageWind} hands it our wind
 * to turn in place of its west, so on a ship IF's direction already is ours.
 *
 * <h2>Leaving IF's declaration alone</h2>
 * IF words its direction differently per file and version: a {@code const} in
 * {@code sway.glsl}, and from 2.2 a {@code #define} choosing the sub-level wind in
 * {@code foliage_legacy.vsh}. With Sable installed IF 2.2 also rewrites its own
 * {@code sway.glsl}, after we do, by finding that exact {@code const} line and renaming
 * every use. So the declaration is never touched: the wind function reads it once into
 * a local of ours, which picks between it and the decoded angle, and every use inside
 * the function reads the local. Whatever IF makes of its name, our local wraps it.
 *
 * <h2>Why the gust wave is crossfaded, not rotated</h2>
 * IF's gust is a travelling wave, {@code sin(-dot(world.xz, direction) * k + t)},
 * where {@code world} is the camera's position wrapped modulo 4096 plus the vertex
 * offset — so typically 1,000-4,000 blocks, almost anywhere in the world. Rotating
 * {@code direction} moves each blade's phase by {@code k * |world| * dAngle}, with
 * {@code k} about 0.35 rad per block. Even ordinary drift of a degree or two a second
 * then shifts phase several radians a second, and a command reversing the wind
 * scrambles it completely every frame: grass glitching back and forth until the
 * rotation stops. IF never rotated its direction, so it never met this.
 * <p>
 * Anchoring the phase near the camera doesn't help. Snapping the anchor to whole
 * wavelengths only ever adds multiples of 2pi, which change nothing; a continuous
 * anchor makes the waves slide along as you walk. What does work is never rotating a
 * wave at all: sixteen stationary waves on fixed compass axes, and the gust is a
 * linear crossfade between the two either side of the wind. No wave ever moves, so
 * nothing scrambles at any rotation speed; and at a sector boundary the outgoing wave
 * has zero weight, so crossing one is seamless. The cost is two {@code sin}s instead
 * of one, and slightly patchier gusts mid-sector, where two band orientations mix.
 * <p>
 * One residual, rarely seen: IF's wrap is chosen so exactly 230 wave cycles fit 4096
 * blocks, which only makes axis-aligned waves seamless across it. A diagonal sector's
 * gust can therefore twitch once as the camera crosses a multiple of 4096 blocks.
 *
 * <h2>Why the storm shake fades in with the lean</h2>
 * IF's wind carries a flutter across it and a tug along it, which its own comments
 * describe as the plant shaking "as if the wind were about to tear it out". They run
 * at about 1.7 and 2.2 Hz, five to seven times faster than the calm sway. IF only
 * ever shows them in rain, which eases in over a few seconds and then holds at full,
 * so the shake always rides on a plant bent well over, where it reads as whipping.
 * Our wind holds a light breeze indefinitely, and there the same shake sat on a plant
 * barely leaning at all, where it reads as bouncing - most visibly near the floor,
 * fading from view around strength 30 as the lean took over (confirmed live).
 * <p>
 * So the shake now ramps in with the lean: none below {@link #SHAKE_START_LEAN} of
 * full lean, IF's full shake from {@link #SHAKE_FULL_LEAN} up. With the default full
 * lean at 50, that's none below strength 20 and full from 40, as tuned live. It works through the {@code shake} argument to IF's wind function, which
 * inside it scales only the flutter and the tug, never the lean.
 */
public final class FoliageShaderPatch {
    /** The names one shader source uses for everything the patch touches. */
    public record Names(String direction, String weather, String scale, String time, String gustSpeed,
            String windFunction, String windCall) {
    }

    /** {@code include/sway.glsl}, spliced into the vanilla terrain and Iris programs. */
    public static final Names SWAY = new Names(
            "MC2_WIND_DIRECTION", "mc2_Weather", "MC2_SWAY_SCALE", "gameTime", "MC2_GUST_SPEED",
            "vec2 mc2_wind(vec3 world, float phase, float gameTime, float reach, float shake) {",
            "mc2_wind(world, phase, gameTime, reach, shake)");

    /** {@code core/foliage_legacy.vsh}, IF's own shader for the Sodium path. */
    public static final Names LEGACY = new Names(
            "WIND_DIRECTION", "Weather", "SWAY_SCALE", "GameTime", "GUST_SPEED",
            "vec2 wind(vec3 world, float phase, float reach, float shake) {",
            "wind(world, phase, reach, shake)");

    /** Added to the angle before negating; anything above pi keeps an angle of 0 negative. */
    public static final float ANGLE_OFFSET = 10.0f;

    /** A {@code w} below this carries an angle: every encoded one is under {@code -(ANGLE_OFFSET - pi)}. */
    public static final float ENCODED_ANGLE_BELOW = -5.0f;

    /** Our wind, blowing the way IF's own direction says: sent while IF draws a Sable sub-level. */
    public static final float OWN_DIRECTION = -1.0f;

    /** IF's {@code WEATHER_WIND_FADE_BLOCKS}: the fade width {@code w} used to carry. */
    public static final float IF_FADE_BLOCKS = 4.0f;

    /** Fraction of full lean below which there is no storm shake at all: strength 20 by default. */
    public static final float SHAKE_START_LEAN = 0.4f;

    /** Fraction of full lean from which IF's full storm shake applies: strength 40 by default. */
    public static final float SHAKE_FULL_LEAN = 0.8f;

    /** How many fixed axes the gust crossfades between. */
    public static final int GUST_SECTORS = 16;

    /** The local the wind function reads its direction from once patched. */
    private static final String DIRECTION = "aeroweather_direction";

    private FoliageShaderPatch() {
    }

    /**
     * The source with its direction taken from {@code weather.w}, its gust crossfaded
     * and its storm shake ramped in with the lean, or {@code null} if anything it
     * touches isn't there exactly as expected — the caller then leaves the shader as IF
     * wrote it, which is always safe.
     */
    public static String patch(String source, Names names) {
        String w = names.weather() + ".w";
        String fade = names.weather() + ".z - " + w;
        String gustOriginal = "0.5 + 0.5 * sin(along * " + names.scale() + " + "
                + names.time() + " * " + names.gustSpeed() + ")";
        String gustLine = "float gust = " + gustOriginal + ";";
        if (count(source, names.windFunction()) != 1 || count(source, fade) != 1
                || count(source, names.windCall()) != 1) {
            return null;
        }
        int open = source.indexOf(names.windFunction()) + names.windFunction().length();
        int close = source.indexOf("\n}", open);
        if (close < 0) {
            return null;
        }
        String body = source.substring(open, close);
        // The direction is used inside the wind function and declared exactly once outside it,
        // however that declaration is worded. A use anywhere else is text we haven't seen.
        Pattern direction = Pattern.compile("\\b" + Pattern.quote(names.direction()) + "\\b");
        int inBody = count(direction, body);
        if (inBody == 0 || count(direction, source) - inBody != 1 || count(body, gustLine) != 1) {
            return null;
        }

        String encoded = w + " < 0.0";
        String angle = "(-" + w + " - " + ANGLE_OFFSET + ")";

        // The one read of IF's direction; IF's own text is used verbatim unless w carries an angle.
        String local = "vec2 " + DIRECTION + " = " + w + " < " + ENCODED_ANGLE_BELOW
                + " ? vec2(cos" + angle + ", sin" + angle + ") : " + names.direction() + ";";

        String fadeWithSentinel = names.weather() + ".z - (" + encoded + " ? " + IF_FADE_BLOCKS + " : " + w + ")";

        // Statements, not one expression, since the line sits inside the function body. IF's
        // own `along` line above is left in place: with the gust no longer reading it, it's
        // dead code the compiler drops. The angle is the local's, not w's, so a sub-level's
        // direction is crossfaded too; a zero vector (wind straight down a tipped ship's
        // stalks) has no angle, and atan(0, 0) is undefined in GLSL.
        String sectorWidth = Float.toString((float) (2.0 * Math.PI / GUST_SECTORS));
        String crossfade = "float aeroweather_angle = dot(" + DIRECTION + ", " + DIRECTION + ") > 0.0"
                + " ? atan(" + DIRECTION + ".y, " + DIRECTION + ".x) : 0.0;\n"
                + "    float aeroweather_sector = aeroweather_angle / " + sectorWidth + ";\n"
                + "    float aeroweather_base = floor(aeroweather_sector);\n"
                + "    float aeroweather_blend = aeroweather_sector - aeroweather_base;\n"
                + "    vec2 aeroweather_axisA = vec2(cos(aeroweather_base * " + sectorWidth + "), "
                + "sin(aeroweather_base * " + sectorWidth + "));\n"
                + "    vec2 aeroweather_axisB = vec2(cos((aeroweather_base + 1.0) * " + sectorWidth + "), "
                + "sin((aeroweather_base + 1.0) * " + sectorWidth + "));\n"
                + "    float gust = " + encoded + "\n"
                + "            ? mix(" + gustAlong("aeroweather_axisA", names) + ",\n"
                + "                  " + gustAlong("aeroweather_axisB", names) + ", aeroweather_blend)\n"
                + "            : " + gustOriginal + ";";

        // IF's own shake is kept verbatim when it isn't handed our direction, like everything else.
        String shakeRamp = "(" + encoded + " ? smoothstep(" + SHAKE_START_LEAN + ", " + SHAKE_FULL_LEAN + ", "
                + names.weather() + ".x) : 1.0)";
        String windCallRamped = names.windCall().replace("shake)", "shake * " + shakeRamp + ")");

        // The local goes in after the uses are renamed, since it's the one place IF's name stays.
        String patchedBody = "\n    " + local
                + direction.matcher(body).replaceAll(DIRECTION).replace(gustLine, crossfade);
        String patched = source.substring(0, open) + patchedBody + source.substring(close);
        return patched.replace(names.windCall(), windCallRamped)
                .replace(fade, fadeWithSentinel);
    }

    /** IF's gust formula, travelling along a fixed {@code axis} instead of the wind direction. */
    private static String gustAlong(String axis, Names names) {
        return "0.5 + 0.5 * sin(-dot(world.xz, " + axis + ") * " + names.scale() + " + "
                + names.time() + " * " + names.gustSpeed() + ")";
    }

    /** The {@code w} to send for a direction: {@code atan2} of the travel vector, encoded negative. */
    public static float encodeAngle(double travelX, double travelZ) {
        return -((float) Math.atan2(travelZ, travelX) + ANGLE_OFFSET);
    }

    private static int count(String text, String target) {
        int count = 0;
        for (int at = text.indexOf(target); at >= 0; at = text.indexOf(target, at + target.length())) {
            count++;
        }
        return count;
    }

    private static int count(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }
}
