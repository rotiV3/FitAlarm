package com.rotiv3.fitalarm.data.model

enum class ActivityType {
    NONE,
    GYM,     // fixed-location training — GPS check-in at saved gym
    OUTDOOR  // runs, walks, hikes, rides — notification confirmation only
}
