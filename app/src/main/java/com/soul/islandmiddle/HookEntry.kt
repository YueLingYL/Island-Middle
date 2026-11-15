package com.soul.islandmiddle

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val FAKE_PROP = "ro.build.characteristics.island_middle_off"
        private val FORCE_ON_LOAD = setOf(
            "miui.systemui.util.CommonUtils",
            "miui.systemui.dynamicisland.DynamicIslandUtils",
            "miuix.os.Build",
            "miui.os.Build",
            "com.miui.utils.configs.MiuiConfigs"
        )
        private val BOOL_FALSE = mapOf(
            "miui.systemui.util.CommonUtils" to listOf("IS_TABLET", "USING_LARGE_SCREEN"),
            "miuix.os.Build" to listOf("IS_TABLET"),
            "miui.os.Build" to listOf("IS_TABLET"),
            "com.miui.utils.configs.MiuiConfigs" to listOf("IS_PAD")
        )
        private val HOOKED = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val xPrefs = XSharedPreferences("com.soul.islandmiddle", "config")
        xPrefs.reload()
        if (!xPrefs.getBoolean("enabled", true)) {
            XposedBridge.log("IslandMiddle: disabled by user, skip")
            return
        }

        hookSystemPropertiesGet(String::class.java)
        hookSystemPropertiesGet(String::class.java, String::class.java)

        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "attach",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    forceAll(lpparam.classLoader)
                    forceAll(HookEntry::class.java.classLoader)
                    forceAll(ClassLoader.getSystemClassLoader())
                    try {
                        val ctx = param.args[0] as android.content.Context
                        forceAll(ctx.classLoader)
                    } catch (_: Throwable) {
                    }
                }
            }
        )

        val afterLoad = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val name = param.args[0] as? String ?: return
                if (name !in FORCE_ON_LOAD) return
                val clazz = param.result as? Class<*> ?: return
                patchClass(clazz)
            }
        }
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java,
                "loadClass",
                String::class.java,
                afterLoad
            )
        } catch (t: Throwable) {
            XposedBridge.log("IslandMiddle: loadClass(String) hook skip: $t")
        }
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader::class.java,
                "loadClass",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                afterLoad
            )
        } catch (t: Throwable) {
            XposedBridge.log("IslandMiddle: loadClass(String,boolean) hook skip: $t")
        }

        XposedBridge.log("IslandMiddle: hooks installed for com.android.systemui")
    }

    private fun hookSystemPropertiesGet(vararg paramTypes: Class<*>) {
        XposedHelpers.findAndHookMethod(
            "android.os.SystemProperties",
            null,
            "get",
            *paramTypes,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == "ro.build.characteristics") {
                        param.args[0] = FAKE_PROP
                    }
                }
            }
        )
    }

    private fun forceAll(cl: ClassLoader?) {
        if (cl == null) return
        for (name in FORCE_ON_LOAD) {
            try {
                val clazz = XposedHelpers.findClass(name, cl)
                patchClass(clazz)
            } catch (_: Throwable) {
            }
        }
    }

    private fun patchClass(clazz: Class<*>) {
        forceBoolFields(clazz)
        hookTabletGetters(clazz)
    }

    private fun hookTabletGetters(clazz: Class<*>) {
        val key = clazz.name + "#patched"
        if (!HOOKED.add(key)) return

        // 插件岛逻辑几乎都走 CommonUtils.INSTANCE.getIS_TABLET()
        if (clazz.name == "miui.systemui.util.CommonUtils") {
            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getIS_TABLET",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result == true) param.result = false
                        }
                    }
                )
                XposedBridge.log("IslandMiddle: hooked CommonUtils.getIS_TABLET -> false")
            } catch (t: Throwable) {
                XposedBridge.log("IslandMiddle: hook getIS_TABLET failed: $t")
            }
            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getForceVertical",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result == true) param.result = false
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }

        // isDynamicIslandPadNoPropOnce = getIS_TABLET() && ...
        if (clazz.name == "miui.systemui.dynamicisland.DynamicIslandUtils") {
            try {
                val m = clazz.declaredMethods.firstOrNull {
                    it.name == "isDynamicIslandPadNoPropOnce" && it.parameterTypes.size == 1
                }
                if (m != null) {
                    XposedHelpers.findAndHookMethod(
                        clazz,
                        "isDynamicIslandPadNoPropOnce",
                        m.parameterTypes[0],
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (param.result == true) param.result = false
                            }
                        }
                    )
                    XposedBridge.log("IslandMiddle: hooked isDynamicIslandPadNoPropOnce -> false")
                }
            } catch (t: Throwable) {
                XposedBridge.log("IslandMiddle: hook padNoPropOnce failed: $t")
            }
        }
    }

    private fun forceBoolFields(clazz: Class<*>) {
        val names = BOOL_FALSE[clazz.name] ?: return
        for (n in names) {
            forceFieldFalse(clazz, n)
        }
    }

    private fun forceFieldFalse(clazz: Class<*>, name: String) {
        val field: Field = try {
            clazz.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            return
        }
        field.isAccessible = true
        val cur = try {
            field.getBoolean(null)
        } catch (_: Throwable) {
            return
        }
        if (!cur) return

        // 去掉 final 后再 set（部分系统可用）
        try {
            val modifiersField = Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        } catch (_: Throwable) {
        }
        try {
            field.setBoolean(null, false)
            XposedBridge.log("IslandMiddle: ${clazz.name}.$name forced false via Field")
            return
        } catch (_: Throwable) {
        }

        // XposedHelpers / Unsafe
        try {
            val unsafe = XposedHelpers.getStaticObjectField(
                Class.forName("sun.misc.Unsafe"),
                "theUnsafe"
            )
            val offset = XposedHelpers.callMethod(unsafe, "objectFieldOffset", field) as Long
            XposedHelpers.callMethod(unsafe, "putBoolean", null, offset, false)
            val now = try {
                field.getBoolean(null)
            } catch (_: Throwable) {
                false
            }
            if (!now) {
                XposedBridge.log("IslandMiddle: ${clazz.name}.$name forced false via Unsafe")
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("IslandMiddle: ${clazz.name}.$name unsafe failed: $t")
        }

        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            val unsafe = theUnsafe.get(null)
            val offset = unsafeClass
                .getMethod("objectFieldOffset", Field::class.java)
                .invoke(unsafe, field) as Long
            unsafeClass
                .getMethod(
                    "putBoolean",
                    Any::class.java,
                    Long::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                .invoke(unsafe, null, offset, false)
            XposedBridge.log("IslandMiddle: ${clazz.name}.$name forced false via raw Unsafe")
        } catch (t: Throwable) {
            XposedBridge.log("IslandMiddle: ${clazz.name}.$name force failed: $t")
        }
    }
}
