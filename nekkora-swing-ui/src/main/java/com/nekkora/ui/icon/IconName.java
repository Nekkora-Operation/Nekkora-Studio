package com.nekkora.ui.icon;

/**
 * Ícones Nekkora (mesmo desenho da nekkora-lib web). Gerado por {@code tools/gen-icon-enum.mjs}.
 */
public enum IconName {
    HOME("home", IconCategory.NAVIGATION),
    STUDIO("studio", IconCategory.NAVIGATION),
    LAYERS("layers", IconCategory.NAVIGATION),
    PLAY("play", IconCategory.NAVIGATION),
    PLAY_CIRCLE("play-circle", IconCategory.NAVIGATION),
    EXECUTIONS("executions", IconCategory.NAVIGATION),
    AGENT("agent", IconCategory.NAVIGATION),
    CHART("chart", IconCategory.NAVIGATION),
    WORKSPACE("workspace", IconCategory.NAVIGATION),
    USERS("users", IconCategory.NAVIGATION),
    USER("user", IconCategory.NAVIGATION),
    LINK("link", IconCategory.NAVIGATION),
    SETTINGS("settings", IconCategory.NAVIGATION),
    SEARCH("search", IconCategory.ACTIONS),
    BELL("bell", IconCategory.ACTIONS),
    PLUS("plus", IconCategory.ACTIONS),
    MINUS("minus", IconCategory.ACTIONS),
    CHECK("check", IconCategory.ACTIONS),
    CLOSE("close", IconCategory.ACTIONS),
    EDIT("edit", IconCategory.ACTIONS),
    TRASH("trash", IconCategory.ACTIONS),
    COPY("copy", IconCategory.ACTIONS),
    CODE("code", IconCategory.ACTIONS),
    DOWNLOAD("download", IconCategory.ACTIONS),
    UPLOAD("upload", IconCategory.ACTIONS),
    FILTER("filter", IconCategory.ACTIONS),
    REFRESH("refresh", IconCategory.ACTIONS),
    MORE_HORIZONTAL("more-horizontal", IconCategory.ACTIONS),
    MORE_VERTICAL("more-vertical", IconCategory.ACTIONS),
    EYE("eye", IconCategory.ACTIONS),
    EYE_OFF("eye-off", IconCategory.ACTIONS),
    SERVER("server", IconCategory.SERVICES),
    SHIELD("shield", IconCategory.SERVICES),
    HEADSET("headset", IconCategory.SERVICES),
    MESSAGE("message", IconCategory.SERVICES),
    PULSE("pulse", IconCategory.SERVICES),
    WORKFLOW("workflow", IconCategory.SERVICES),
    SEND("send", IconCategory.SERVICES),
    MENU("menu", IconCategory.SERVICES),
    INSTAGRAM("instagram", IconCategory.BRANDS),
    LINKEDIN("linkedin", IconCategory.BRANDS),
    WHATSAPP("whatsapp", IconCategory.BRANDS),
    CHEVRON_DOWN("chevron-down", IconCategory.ARROWS),
    CHEVRON_UP("chevron-up", IconCategory.ARROWS),
    CHEVRON_LEFT("chevron-left", IconCategory.ARROWS),
    CHEVRON_RIGHT("chevron-right", IconCategory.ARROWS),
    ARROW_RIGHT("arrow-right", IconCategory.ARROWS),
    ARROW_LEFT("arrow-left", IconCategory.ARROWS),
    ARROW_UP("arrow-up", IconCategory.ARROWS),
    ARROW_DOWN("arrow-down", IconCategory.ARROWS),
    EXTERNAL_LINK("external-link", IconCategory.ARROWS),
    INFO("info", IconCategory.STATUS),
    ALERT("alert", IconCategory.STATUS),
    CHECK_CIRCLE("check-circle", IconCategory.STATUS),
    X_CIRCLE("x-circle", IconCategory.STATUS),
    CLOCK("clock", IconCategory.STATUS),
    LOADER("loader", IconCategory.STATUS),
    SUN("sun", IconCategory.THEME),
    MOON("moon", IconCategory.THEME),
    BOX("box", IconCategory.OBJECTS),
    MONITOR("monitor", IconCategory.OBJECTS),
    FILE("file", IconCategory.OBJECTS),
    FOLDER("folder", IconCategory.OBJECTS),
    CALENDAR("calendar", IconCategory.OBJECTS),
    MAIL("mail", IconCategory.OBJECTS),
    PHONE("phone", IconCategory.OBJECTS),
    SMARTPHONE("smartphone", IconCategory.OBJECTS),
    ID_CARD("id-card", IconCategory.OBJECTS),
    BUILDING("building", IconCategory.OBJECTS),
    LOCK("lock", IconCategory.OBJECTS),
    STAR("star", IconCategory.OBJECTS),
    ROBOT("robot", IconCategory.OBJECTS),
    SAKURA("sakura", IconCategory.OBJECTS),
    PAUSE("pause", IconCategory.ACTIONS),
    STOP("stop", IconCategory.ACTIONS),
    SAVE("save", IconCategory.ACTIONS),
    TERMINAL("terminal", IconCategory.OBJECTS),
    SLIDERS("sliders", IconCategory.ACTIONS),
    PANEL_LEFT("panel-left", IconCategory.LAYOUT),
    PANEL_RIGHT("panel-right", IconCategory.LAYOUT),
    LIST("list", IconCategory.LAYOUT),
    GRID("grid", IconCategory.LAYOUT),
    HISTORY("history", IconCategory.OBJECTS),
    TAG("tag", IconCategory.OBJECTS),
    SORT("sort", IconCategory.ARROWS),
    FOLDER_OPEN("folder-open", IconCategory.OBJECTS),
    WIFI_OFF("wifi-off", IconCategory.STATUS),
    KEY("key", IconCategory.OBJECTS),
    COMMAND("command", IconCategory.ACTIONS),
    BRACES("braces", IconCategory.OBJECTS),
    LOCK_OPEN("lock-open", IconCategory.OBJECTS),
    CIRCLE_DASHED("circle-dashed", IconCategory.STATUS),
    MINUS_CIRCLE("minus-circle", IconCategory.STATUS),
    PAUSE_CIRCLE("pause-circle", IconCategory.STATUS),
    IMAGE("image", IconCategory.OBJECTS);

    private final String id;
    private final IconCategory category;

    IconName(String id, IconCategory category) {
        this.id = id;
        this.category = category;
    }

    /** Nome do ícone no conjunto (ex.: {@code chevron-down}). */
    public String id() {
        return id;
    }

    public IconCategory category() {
        return category;
    }
}
