package com.cauldronjs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.cauldronjs.utils.Console;
import com.cauldronjs.utils.PathHelpers;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.SpongeExecutorService.SpongeFuture;

@Plugin(id = "cauldron", name = "Cauldron", version = "1.0.0", description = "NodeJS environment for Sponge")
public class Cauldron implements CauldronAPI {
  private static Cauldron instance;

  private Isolate mainIsolate;
  private TargetDescriptor targetDescriptor;
  private boolean isDebugging = true;
  private SpongeExecutorService executorService;

  private HashMap<Integer, SpongeFuture<?>> scheduledTasks = new HashMap<>();

  @Inject
  private Logger logger;

  @Inject
  @ConfigDir(sharedRoot = false)
  private Path configDirectory;

  @Inject
  private PluginContainer pluginContainer;

  @Inject
  private Game sponge;

  public Cauldron() {
    instance = this;
    this.targetDescriptor = new TargetDescriptor("sponge", null);
  }

  @Listener
  public void onServerStart(GameInitializationEvent event) {
    this.executorService = this.sponge.getScheduler().createSyncExecutor(this);
    try {
      PathHelpers.tryInitializeCwd(this);
    } catch (IOException ex) {
      this.log(Level.WARNING, "Failed to instantiate cwd");
    }
    this.mainIsolate = new Isolate(this);
    this.mainIsolate.scope();
    Console.log("Finished initializing Cauldron");
  }

  @Override
  public boolean cancelTask(int taskId) {
    SpongeFuture<?> future = this.scheduledTasks.get(taskId);
    if (future == null)
      return false;
    if (!future.isDone() && !future.isCancelled()) {
      future.cancel(true);
    }
    return true;
  }

  @Override
  public File cwd() {
    return new File("D:\\dev\\cauldron-scripts");
  }

  @Override
  public Isolate getMainIsolate() {
    return this.mainIsolate;
  }

  @Override
  public InputStream getResource(String name) {
    Optional<Asset> asset = this.pluginContainer.getAsset(name);
    if (!asset.isPresent())
      return null;
    try {
      return new ByteArrayInputStream(asset.get().readBytes());
    } catch (IOException ex) {
      return null;
    }
  }

  @Override
  public TargetDescriptor getTarget() {
    return this.targetDescriptor;
  }

  @Override
  public boolean isDebugging() {
    return this.isDebugging;
  }

  @Override
  public void log(Level level, String msg) {
    if (level == Level.INFO) {
      this.logger.info(msg);
    } else if (level == Level.WARNING) {
      this.logger.info("[WARN] " + msg);
    } else if (level == Level.SEVERE) {
      this.logger.error(msg);
    } else if (level == Level.FINE && this.isDebugging) {
      this.logger.info("[DEBUG] " + msg);
    } else {
      this.logger.info("[UNCATEGORIZED] " + msg);
    }
  }

  @Override
  public int scheduleRepeatingTask(Runnable runnable, int interval, int timeout) {
    SpongeFuture<?> future = this.executorService.scheduleAtFixedRate(runnable, 0, interval, TimeUnit.MILLISECONDS);
    this.scheduledTasks.put(future.hashCode(), future);
    return future.hashCode();
  }

  @Override
  public int scheduleTask(Runnable runnable, int delay) {
    SpongeFuture<?> future = this.executorService.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    this.scheduledTasks.put(future.hashCode(), future);
    return future.hashCode();
  }

}