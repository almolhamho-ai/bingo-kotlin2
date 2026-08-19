package com.example.quickgestures.data

/**
 * كتالوج كل الإجراءات المتاحة (تستخدم بالكرة العائمة، إيماءات الحافة، والروتينات).
 */
data class GestureAction(
    val id: String,
    val displayLabel: String,
    val category: ActionCategory
)

enum class ActionCategory {
    SYSTEM, MEDIA, APP_LAUNCH, TOGGLE, CUSTOM
}

object GestureActionCatalog {
    val all: List<GestureAction> = listOf(
        GestureAction("flashlight_toggle", "الفلاش", ActionCategory.TOGGLE),
        GestureAction("screenshot", "لقطة شاشة", ActionCategory.SYSTEM),
        GestureAction("back", "رجوع", ActionCategory.SYSTEM),
        GestureAction("home", "الرئيسية", ActionCategory.SYSTEM),
        GestureAction("recents", "التطبيقات الأخيرة", ActionCategory.SYSTEM),
        GestureAction("volume_mute", "كتم الصوت", ActionCategory.MEDIA),
        GestureAction("media_play_pause", "تشغيل/إيقاف الوسائط", ActionCategory.MEDIA),
        GestureAction("wifi_toggle", "الواي فاي", ActionCategory.TOGGLE),
        GestureAction("bt_toggle", "البلوتوث", ActionCategory.TOGGLE),
        GestureAction("dnd_toggle", "عدم الإزعاج", ActionCategory.TOGGLE),
        GestureAction("start_recording", "بدء التسجيل الشفاف", ActionCategory.CUSTOM),
        GestureAction("open_app", "فتح تطبيق محدد", ActionCategory.APP_LAUNCH)
    )

    fun byId(id: String): GestureAction? = all.firstOrNull { it.id == id }
}
