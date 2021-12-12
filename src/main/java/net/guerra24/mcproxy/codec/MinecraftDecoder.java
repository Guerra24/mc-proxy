package net.guerra24.mcproxy.codec;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;

import com.google.gson.stream.JsonWriter;

import io.graversen.minecraft.rcon.Defaults;
import io.graversen.minecraft.rcon.service.ConnectOptions;
import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.graversen.minecraft.rcon.service.RconDetails;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import net.guerra24.mcproxy.Main;
import net.guerra24.mcproxy.Monitor;
import net.guerra24.mcproxy.SocketState;
import net.guerra24.mcproxy.util.PacketUtil;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;

public class MinecraftDecoder extends ChannelInboundHandlerAdapter {
	final static AttributeKey<SocketState> SOCKET_STATE = AttributeKey.valueOf("socketstate");
	final static AttributeKey<Channel> PROXY_CHANNEL = AttributeKey.valueOf("proxychannel");

	@Override
	public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
		SocketState socketState = ctx.channel().attr(SOCKET_STATE).get();
		if (socketState != SocketState.PROXY) {
			final ByteBuf buf = (ByteBuf) msg;
			if (Main.running && Main.ready) {
				handOff(ctx, buf);
				return;
			}
			ctx.channel().attr(SOCKET_STATE).set(SocketState.HANDSHAKE);
			final int packetLength = readVarInt(buf);
			final int packetID = readVarInt(buf);
			// System.out.println("PacketId: " + packetID);
			// System.out.println("PacketLength: " + packetLength);
			switch (packetID) {
			case 0:
				packet0(ctx, buf, packetLength, packetID);
				break;
			case 1:
				packet1(ctx, buf, packetLength, packetID);
				break;
			}
		} else {
			Channel proxiedChannel = ctx.channel().attr(PROXY_CHANNEL).get();
			proxiedChannel.writeAndFlush(msg);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		SocketState socketState = ctx.channel().attr(SOCKET_STATE).get();
		if (socketState == SocketState.PROXY) {
			Channel proxiedChannel = ctx.channel().attr(PROXY_CHANNEL).get();
			proxiedChannel.close();
		}
		super.channelInactive(ctx);
	}

	private void packet0(final ChannelHandlerContext ctx, final ByteBuf buf, int packetLength, int packetID) {
		final int protocolVersion = readVarInt(buf);
		final String hostname = readString(buf);
		final int port = buf.readUnsignedShort();
		final int state = readVarInt(buf);
		// System.out.println(protocolVersion);
		// System.out.println(hostname);
		// System.out.println(port);
		// System.out.println(state);

		switch (state) {
		case 1: // Handshake
			var message = Main.getSettings().getMotd();
			if (Main.running && !Main.ready)
				message = "Instance is starting... please wait";
			ByteBuf body = PacketUtil.createStatusPacket(protocolVersion, message);
			ByteBuf header = Unpooled.buffer();

			writeVarInt(body.readableBytes(), header);

			ctx.channel().writeAndFlush(header);
			ctx.channel().writeAndFlush(body);

			break;
		case 2: // Login
			var rewind = buf.readerIndex();
			var user = readString(buf).substring(2);
			buf.readerIndex(rewind);
			if (!Main.getSettings().getWhitelist().contains(user)) {
				disconnect(ctx, "Not found in whitelist");
				return;
			}

			if (Main.running && !Main.ready) {
				disconnect(ctx, "Instance is starting... please wait");
				return;
			}
			Main.ready = false;
			Main.starting = true;
			new Thread(() -> {
				System.out.println("Instance Starting");
				var startup = StartInstancesRequest.builder().instanceIds(Main.getSettings().getInstance()).build();
				try {
					Main.getEc2().startInstances(startup);
				} catch (Exception e) {
					Main.starting = false;
					return;
				}
				Main.running = true;

				Instance instance = null;
				while (true) {
					var describe = DescribeInstancesRequest.builder().instanceIds(Main.getSettings().getInstance())
							.build();
					var result = Main.getEc2().describeInstances(describe);
					instance = result.reservations().get(0).instances().get(0);
					if (instance.state().name() == InstanceStateName.RUNNING)
						break;
					try {
						Thread.sleep(2500);
					} catch (InterruptedException e) {
					}
				}
				System.out.println("Instance Running");
				Main.instanceAddress = instance.privateIpAddress();

				System.out.println("Initializing RCON");
				Main.rconService = new MinecraftRconService(
						new RconDetails(Main.instanceAddress, Main.getSettings().getRconPort(),
								Main.getSettings().getRconPassword()),
						new ConnectOptions(200, Duration.ofSeconds(1), Defaults.CONNECTION_WATCHER_INTERVAL));

				Main.rconService.connectBlocking(Duration.ofSeconds(120));

				Main.monitorThread = new Monitor();
				Main.monitorThread.start();

				System.out.println("Hand-off to server...");
				Main.ready = true;
			}).start();

			//int timeout = 0;
			while (true) {
				if (Main.ready)
					break;
				/*if (timeout >= 27500) {
					disconnect(ctx, "Server is taking too long to start... please try again");
					return;
				}*/
				if (!Main.starting) {
					disconnect(ctx, "Unable to start instance... please try again in 5 minutes");
					return;
				}
				if (Main.running && !Main.ready) {
					disconnect(ctx, "Instance is starting... please wait");
					return;
				}
				//timeout += 1000;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
/*
			Bootstrap b = new Bootstrap();
			b.group(Main.getWorkerGroup());
			b.channel(NioSocketChannel.class);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
			b.handler(new ChannelInitializer<Channel>() {
				@Override
				public void initChannel(Channel ch) throws Exception {
					ch.pipeline().addLast(new ProxyHandler(ctx.channel()));
				}
			});
			final ChannelFuture cf = b.connect(Main.instanceAddress, Main.getSettings().getRemotePort());

			cf.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						ByteBuf sendBuf = Unpooled.buffer();
						writeVarInt(packetLength, sendBuf);
						writeVarInt(packetID, sendBuf);
						writeVarInt(protocolVersion, sendBuf);
						writeString(hostname, sendBuf);
						writeVarShort(sendBuf, port);
						writeVarInt(state, sendBuf);

						while (buf.readableBytes() > 0) {
							byte b = buf.readByte();
							sendBuf.writeByte(b);
						}

						future.channel().writeAndFlush(sendBuf); // Send out the handshake + anything else we've
																	// gotten (Request or login start packet)
						ctx.channel().attr(SOCKET_STATE).set(SocketState.PROXY);
						ctx.channel().attr(PROXY_CHANNEL).set(cf.channel());
					}
				}
			});*/
			break;
		}
	}

	private void packet1(final ChannelHandlerContext ctx, final ByteBuf buf, int packetLength, int packetID) {
		var ping = buf.readLong();
		ByteBuf packet = Unpooled.buffer();
		writeVarInt(1 + 8, packet);
		writeVarInt(1, packet);
		packet.writeLong(ping);
		ctx.channel().writeAndFlush(packet);
	}

	private void disconnect(final ChannelHandlerContext ctx, String msg) {
		StringWriter sw = new StringWriter();
		try (var writer = new JsonWriter(sw)) {
			writer.beginObject();
			writer.name("text").value(msg);
			writer.endObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ByteBuf message = Unpooled.buffer();
		MinecraftDecoder.writeVarInt(0, message);
		MinecraftDecoder.writeString(sw.toString(), message);

		ByteBuf packet = Unpooled.buffer();
		writeVarInt(message.readableBytes(), packet);
		packet.writeBytes(message);

		ctx.channel().writeAndFlush(packet);
		ctx.close();
	}

	private void handOff(final ChannelHandlerContext ctx, final ByteBuf buf) {
		Bootstrap b = new Bootstrap();
		b.group(Main.getWorkerGroup());
		b.channel(NioSocketChannel.class);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
		b.handler(new ChannelInitializer<Channel>() {
			@Override
			public void initChannel(Channel ch) throws Exception {
				ch.pipeline().addLast(new ProxyHandler(ctx.channel()));
			}
		});
		final ChannelFuture cf = b.connect(Main.instanceAddress, Main.getSettings().getRemotePort());

		cf.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					future.channel().writeAndFlush(buf);
					ctx.channel().attr(SOCKET_STATE).set(SocketState.PROXY);
					ctx.channel().attr(PROXY_CHANNEL).set(cf.channel());
				}
			}
		});
	}

	/*
	 * All of these varint implementations comes from SpigotMC's bungeecord. Source:
	 * https://github.com/SpigotMC/BungeeCord/blob/master/protocol/src/main/java/net
	 * /md_5/bungee/protocol/DefinedPacket.java
	 */
	public static int readVarInt(ByteBuf input) {
		return readVarInt(input, 5);
	}

	public static int readVarInt(ByteBuf input, int maxBytes) {
		int value = 0;
		int size = 0;
		int b;
		while (((b = input.readByte()) & 0x80) == 0x80) {
			value |= (b & 0x7F) << (size++ * 7);
			if (size > 5) {
				throw new RuntimeException("VarInt too long (length must be <= 5)");
			}
		}

		return value | ((b & 0x7F) << (size * 7));
	}

	public static String readString(ByteBuf buf) {
		int len = readVarInt(buf);
		if (len > Short.MAX_VALUE) {
		}

		byte[] b = new byte[len];
		buf.readBytes(b);

		return new String(b);
	}

	public static void writeVarInt(int value, ByteBuf output) {
		int part;
		while (true) {
			part = value & 0x7F;

			value >>>= 7;
			if (value != 0) {
				part |= 0x80;
			}

			output.writeByte(part);

			if (value == 0) {
				break;
			}
		}
	}

	public static void writeString(String s, ByteBuf buf) {
		if (s.length() > Short.MAX_VALUE) {
		}

		byte[] b = s.getBytes();
		writeVarInt(b.length, buf);
		buf.writeBytes(b);
	}

	public static void writeVarShort(ByteBuf buf, int toWrite) {
		int low = toWrite & 0x7FFF;
		int high = (toWrite & 0x7F8000) >> 15;
		if (high != 0) {
			low = low | 0x8000;
		}
		buf.writeShort(low);
		if (high != 0) {
			buf.writeByte(high);
		}
	}
}
