package com.rotiv3.fitalarm.data.model

enum class SessionStatus {
    UPCOMING,   // event hasn't started yet
    AT_GYM,     // GPS confirmed user is inside gym radius
    COMPLETED,  // 5-min presence confirmed — session done
    MISSED      // event ended without completion
}
