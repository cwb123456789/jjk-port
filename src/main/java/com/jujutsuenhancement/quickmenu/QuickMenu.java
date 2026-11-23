package com.jujutsuenhancement.quickmenu;

import java.util.List;

/**
 * The menu of the add-on "咒术觉醒快捷指令" ({@code jjkshortcut:quick_command}, scripts/main.js): a main menu with four categories and one
 * form per category whose buttons run a function of the add-on. Here the buttons are run by {@link QuickCommands}.
 * The skin category belongs to the skin-pack add-on and is not part of the main mod.
 */
public final class QuickMenu {
    public record Category(String title, List<String> buttons) {}

    public static final String MAIN_TITLE = "咒术觉醒快捷指令";
    public static final String MAIN_BODY = "选择一个功能分类";
    public static final String SUB_BODY = "点击选项执行快捷指令";
    public static final String BACK = "§7← 返回主菜单";

    public static final List<Category> CATEGORIES = List.of(
            new Category("玩家强化与多项升级", List.of("提升至最高等级", "升级至20指宿傩", "运气值最高", "开/灶的蓄力值充满", "瞬间冷却")),
            new Category("能力与特质获取", List.of("获得天与咒缚体质", "获得咒力缠绕拳", "获得领域展延", "获得反转术式", "获得简易领域", "获得六眼",
                    "学会世界斩击", "获得弥虚葛笼", "调服所有式神（魔虚罗自己打）")),
            new Category("建筑物生成", List.of("生成基岩平台", "生成平滑石平台", "生成咒术高专建筑")));

    private QuickMenu() {}
}
