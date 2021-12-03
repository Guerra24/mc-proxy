package net.guerra24.mcproxy;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.guerra24.mcproxy.codec.MinecraftDecoder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class Main {
	private static EventLoopGroup bossGroup = new NioEventLoopGroup();
	private static EventLoopGroup workerGroup = new NioEventLoopGroup();
	private static Settings settings;
	private static Ec2Client ec2;

	public static String instanceAddress;
	public static boolean running, ready, starting;
	public static MinecraftRconService rconService;
	public static Thread monitorThread;

	public static void main(String args[]) {
		loadSettings();
		configureAWS();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(new MinecraftDecoder());
						}
					}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

			ChannelFuture f = b.bind(settings.getPort()).sync();

			f.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	private static void loadSettings() {
		File file = new File("config/settings.json");
		if (file.exists()) {
			Gson gson = new Gson();
			try (var reader = new FileReader(file)) {
				settings = gson.fromJson(reader, Settings.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// Create default configuration file.
			try {
				file.createNewFile();
			} catch (IOException e) {
				System.out.println("Could not create default configuration file.");
				System.exit(0);
				e.printStackTrace();
			}
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Settings defaultSettings = new Settings();
			defaultSettings.setVersionName("1.18");
			defaultSettings.setMaxPlayers(0);
			defaultSettings.setOnlinePlayers(0);
			defaultSettings.setRconPassword("");
			defaultSettings.setRconPort(25575);
			defaultSettings.setMotd("Server is sleeping... join to start");

			defaultSettings.setPort(25565);

			defaultSettings.setRemotePort(25565);
			defaultSettings.setInstance("");
			defaultSettings.setAccessKey("");
			defaultSettings.setSecretKey("");
			defaultSettings.setWhitelist(new ArrayList<>());

			try (var writer = new FileWriter(file.getAbsolutePath())) {
				gson.toJson(defaultSettings, writer);
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
			settings = defaultSettings;
		}
	}

	private static void configureAWS() {
		AwsBasicCredentials creds = AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey());
		ec2 = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(creds)).build();
	}

	public static Settings getSettings() {
		return settings;
	}

	public static EventLoopGroup getBossGroup() {
		return bossGroup;
	}

	public static EventLoopGroup getWorkerGroup() {
		return workerGroup;
	}

	public static Ec2Client getEc2() {
		return ec2;
	}
}
