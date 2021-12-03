package net.guerra24.mcproxy.util;

import com.google.gson.stream.JsonWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.guerra24.mcproxy.Main;
import net.guerra24.mcproxy.Settings;
import net.guerra24.mcproxy.codec.MinecraftDecoder;

import java.io.IOException;
import java.io.StringWriter;

public class PacketUtil {
	/**
	 * @param protocolVersion protocol verison of connecting client, because we
	 *                        always want to show we are the right version.
	 * @return
	 */
	public static ByteBuf createStatusPacket(int protocolVersion, String message) {
		Settings settings = Main.getSettings();
		StringWriter sw = new StringWriter();
		try (var writer = new JsonWriter(sw)) {
			
			writer.beginObject();
			writer.name("version").beginObject();
			writer.name("name").value(settings.getVersionName());
			writer.name("protocol").value(protocolVersion);
			writer.endObject();

			writer.name("players").beginObject();
			writer.name("max").value(settings.getMaxPlayers());
			writer.name("online").value(settings.getOnlinePlayers());
			writer.name("sample").beginArray();
			writer.endArray();
			writer.endObject();

			writer.name("description").beginObject();
			writer.name("text").value(message);
			writer.endObject();

			writer.endObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

		ByteBuf buf = Unpooled.buffer();
		MinecraftDecoder.writeVarInt(0, buf);

		MinecraftDecoder.writeString(sw.toString(), buf);
		return buf;
	}
}
