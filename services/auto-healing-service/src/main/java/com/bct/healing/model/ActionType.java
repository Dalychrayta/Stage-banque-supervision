package com.bct.healing.model;

public enum ActionType {
    RESTART_SERVICE,
    CLEAR_CACHE,
    SCALE_UP,
    FREE_DISK_SPACE,
    KILL_PROCESS,
    NOTIFY_TEAM,
    RECOMMEND_ONLY  // Quand l'action automatique n'est pas possible
}
