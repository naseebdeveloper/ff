package com.trafficracerbot

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores recorded button positions (X, Y coordinates) for each game control.
 * Saved to SharedPreferences so they persist between sessions.
 */
data class MacroPoint(val x: Float, val y: Float)

object MacroData {

    private const val PREFS_NAME = "TrafficBotMacros"

    private const val KEY_ACCEL_X  = "accel_x"
    private const val KEY_ACCEL_Y  = "accel_y"
    private const val KEY_BRAKE_X  = "brake_x"
    private const val KEY_BRAKE_Y  = "brake_y"
    private const val KEY_HORN_X   = "horn_x"
    private const val KEY_HORN_Y   = "horn_y"
    private const val KEY_STEER_L_X = "steer_l_x"
    private const val KEY_STEER_L_Y = "steer_l_y"
    private const val KEY_STEER_R_X = "steer_r_x"
    private const val KEY_STEER_R_Y = "steer_r_y"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAccelerate(ctx: Context, x: Float, y: Float) =
        prefs(ctx).edit().putFloat(KEY_ACCEL_X, x).putFloat(KEY_ACCEL_Y, y).apply()

    fun saveBrake(ctx: Context, x: Float, y: Float) =
        prefs(ctx).edit().putFloat(KEY_BRAKE_X, x).putFloat(KEY_BRAKE_Y, y).apply()

    fun saveHorn(ctx: Context, x: Float, y: Float) =
        prefs(ctx).edit().putFloat(KEY_HORN_X, x).putFloat(KEY_HORN_Y, y).apply()

    fun saveSteerLeft(ctx: Context, x: Float, y: Float) =
        prefs(ctx).edit().putFloat(KEY_STEER_L_X, x).putFloat(KEY_STEER_L_Y, y).apply()

    fun saveSteerRight(ctx: Context, x: Float, y: Float) =
        prefs(ctx).edit().putFloat(KEY_STEER_R_X, x).putFloat(KEY_STEER_R_Y, y).apply()

    fun getAccelerate(ctx: Context): MacroPoint? =
        getPoint(ctx, KEY_ACCEL_X, KEY_ACCEL_Y)

    fun getBrake(ctx: Context): MacroPoint? =
        getPoint(ctx, KEY_BRAKE_X, KEY_BRAKE_Y)

    fun getHorn(ctx: Context): MacroPoint? =
        getPoint(ctx, KEY_HORN_X, KEY_HORN_Y)

    fun getSteerLeft(ctx: Context): MacroPoint? =
        getPoint(ctx, KEY_STEER_L_X, KEY_STEER_L_Y)

    fun getSteerRight(ctx: Context): MacroPoint? =
        getPoint(ctx, KEY_STEER_R_X, KEY_STEER_R_Y)

    private fun getPoint(ctx: Context, kx: String, ky: String): MacroPoint? {
        val p = prefs(ctx)
        val x = p.getFloat(kx, -1f)
        val y = p.getFloat(ky, -1f)
        return if (x < 0 || y < 0) null else MacroPoint(x, y)
    }

    fun allRecorded(ctx: Context): Boolean =
        getAccelerate(ctx) != null &&
        getBrake(ctx) != null &&
        getHorn(ctx) != null &&
        getSteerLeft(ctx) != null &&
        getSteerRight(ctx) != null

    fun clearAll(ctx: Context) =
        prefs(ctx).edit().clear().apply()
}
