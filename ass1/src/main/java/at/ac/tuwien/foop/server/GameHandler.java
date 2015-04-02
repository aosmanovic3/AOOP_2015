package at.ac.tuwien.foop.server;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.ac.tuwien.foop.message.JoinMessage;
import at.ac.tuwien.foop.message.Message;
import at.ac.tuwien.foop.message.Message.Type;
import at.ac.tuwien.foop.server.model.Game;
import at.ac.tuwien.foop.server.model.Player;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GameHandler extends ChannelHandlerAdapter {
	private static Logger log = LoggerFactory.getLogger(GameHandler.class);

	private ObjectMapper mapper = new ObjectMapper();
	private Game game;
	private Player player;

	public GameHandler(Game game) {
		this.game = game;
	}

	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		String str = (String) msg;
		log.info(String.format("server received msg: %s", str));

		Message m = mapper.readValue(str, Message.class);

		if (m.type == Type.C_PING) {
			ctx.writeAndFlush(new Message(Type.S_PONG));
		} else if (m.type == Type.C_JOIN) {
			JoinMessage jm = mapper.readValue(str, JoinMessage.class);
			if (player == null) {
				player = new Player(jm.name);
			}
			game.join(player);
			ctx.writeAndFlush(new Message(Type.S_JOINED));
		} else {
			log.warn("unknown message");
			ctx.writeAndFlush(new Message(Type.S_ALREADY_FULL));
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		log.info("new client connected");
	}

	// TODO: should be triggered by the UI
	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		super.channelUnregistered(ctx);
		ctx.channel().close();
		ctx.channel().parent().close();
	}
}
