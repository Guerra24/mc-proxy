package net.guerra24.mcproxy;

import java.time.Duration;

import io.graversen.minecraft.rcon.MinecraftRcon;
import io.graversen.minecraft.rcon.commands.PlayerListCommand;
import io.graversen.minecraft.rcon.query.playerlist.PlayerNamesMapper;

public class Monitor extends Thread {
	private final PlayerNamesMapper playerNamesMapper = new PlayerNamesMapper();

	private Thread watchdog;

	public Monitor() {
		setName("Monitor");
		watchdog = new Thread(() -> {
			while (Main.running) {
				try {
					System.out.println("Waiting for empty server...");
					Thread.sleep(Duration.ofMinutes(Main.getSettings().getSleepTime()).toMillis());
				} catch (InterruptedException e) {
					System.out.println("Shutdown interrupted");
					continue;
				}
				System.out.println("Server empty... shutting down");
				Main.rconService.disconnect();
				Main.running = false;
				Main.ready = false;
				System.out.println("Stopping instance");
				Main.getEc2().stopInstances((b) -> b.instanceIds(Main.getSettings().getInstance()).build());
			}
		});
		watchdog.setName("Watchdog");
		watchdog.start();
	}

	@Override
	public void run() {
		MinecraftRcon rcon;
		while (true) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			var opt = Main.rconService.minecraftRcon();
			if (opt.isPresent()) {
				rcon = opt.get();
				break;
			}
		}
		System.out.println("Monitor started. Waiting " + Main.getSettings().getSleepTime() + " minutes");
		int retries = 0;
		while (Main.running) {
			try {
				Thread.sleep(30000);
			} catch (InterruptedException e) {
			}
			if (!Main.rconService.isConnected()) {
				if (retries > 5) {
					Main.rconService.disconnect();
					Main.running = false;
					Main.ready = false;
					return;
				}
				retries++;
				continue;
			} else {
				retries = 0;
			}
			var list = PlayerListCommand.names();
			var result = rcon.sendSync(list);
			final var playerList = playerNamesMapper.apply(result);
			if (playerList.getPlayerNames().size() != 0)
				watchdog.interrupt();
		}
	}

}
