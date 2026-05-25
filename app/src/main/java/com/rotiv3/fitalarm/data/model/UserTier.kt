package com.rotiv3.fitalarm.data.model

enum class UserTier {
    FREE, PRO;

    val label: String get() = if (this == PRO) "Pro ⚡" else "Free"
    val isPro: Boolean get() = this == PRO
}
