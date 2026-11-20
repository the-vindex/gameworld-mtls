package net.gameworld.mtls;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

/** First in the pipeline of a gate connection that could not get TLS: no byte is ever written. */
@ChannelHandler.Sharable
public final class BlockAll extends ChannelOutboundHandlerAdapter {
    public static final BlockAll INSTANCE = new BlockAll();

    private BlockAll() {}

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ReferenceCountUtil.release(msg);
        promise.setFailure(new IOException("gameworld-mtls: connection refused locally (no TLS)"));
    }
}
