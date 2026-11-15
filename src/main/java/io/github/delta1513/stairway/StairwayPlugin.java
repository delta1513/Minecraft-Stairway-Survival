package io.github.delta1513.stairway;

import io.github.delta1513.stairway.config.GameConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class StairwayPlugin extends JavaPlugin {

    private GameConfig gameConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameConfig = GameConfig.from(getConfig());
    }

    public GameConfig gameConfig() {
        return gameConfig;
    }
}
