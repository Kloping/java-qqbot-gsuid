package io.github.kloping;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.kloping.MySpringTool.PartUtils;
import io.github.kloping.MySpringTool.h1.impl.ConfigFileManagerImpl;
import io.github.kloping.MySpringTool.h1.impl.component.ContextManagerWithEIImpl;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.MySpringTool.interfaces.component.ConfigFileManager;
import io.github.kloping.MySpringTool.interfaces.component.ContextManager;
import io.github.kloping.common.Public;
import io.github.kloping.judge.Judge;
import io.github.kloping.qqbot.Starter;
import io.github.kloping.qqbot.api.Intents;
import io.github.kloping.qqbot.api.SendAble;
import io.github.kloping.qqbot.api.event.ConnectedEvent;
import io.github.kloping.qqbot.api.message.MessageChannelReceiveEvent;
import io.github.kloping.qqbot.api.message.MessageEvent;
import io.github.kloping.qqbot.api.v2.GroupMessageEvent;
import io.github.kloping.qqbot.entities.ex.At;
import io.github.kloping.qqbot.entities.ex.Image;
import io.github.kloping.qqbot.entities.ex.MessageAsyncBuilder;
import io.github.kloping.qqbot.entities.ex.PlainText;
import io.github.kloping.qqbot.impl.ListenerHost;
import io.github.kloping.qqbot.impl.message.BaseMessageChannelReceiveEvent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author github.kloping
 */
public class GenshinUidClient extends WebSocketClient {
    public static final String APPID_KEY = "appid";
    public static final String TOKEN_KEY = "token";
    public static final String SECRET_KEY = "secret";
    public static final String CODE_KEY = "type";
    public static final String WSS_IP_KEY = "wss.ip";
    public static final String WSS_PORT_KEY = "wss.port";
    public static final String UPLOAD_URL_KEY = "upload.url";

    public static final String DEFAULT_CONFIG_STRING = "";

    public static void main(String[] args) {
        ContextManager contextManager = new ContextManagerWithEIImpl();
        ConfigFileManager configFileManager = new ConfigFileManagerImpl(contextManager);
        configFileManager.load("./config.txt");
        testConfigAndTryStart(contextManager);
    }

    private static void testConfigAndTryStart(ContextManager contextManager) {
        Integer appid = contextManager.getContextEntity(Integer.class, APPID_KEY);
        String token = contextManager.getContextEntity(String.class, TOKEN_KEY);
        String secret = contextManager.getContextEntity(String.class, SECRET_KEY);
        Integer code = contextManager.getContextEntity(Integer.class, CODE_KEY);
        String wssIp = contextManager.getContextEntity(String.class, WSS_IP_KEY);
        String upUrl = contextManager.getContextEntity(String.class, UPLOAD_URL_KEY);
        Integer wssPort = contextManager.getContextEntity(Integer.class, WSS_PORT_KEY);
        if (Judge.isEmpty(token, wssIp) || appid == null || code == null || wssPort == null) {
            System.err.println("配置不全,请重新配置(config.txt)后重启程序");
            return;
        }
        Starter starter;
        if (secret == null) starter = new Starter(appid.toString(), token);
        else starter = new Starter(appid.toString(), token, secret);
        if (code == 0) starter.getConfig().setCode(Intents.PRIVATE_INTENTS.getCode());
        else if (code == 1) starter.getConfig().setCode(Intents.PUBLIC_INTENTS.getCode());
        else if (code == 2) starter.getConfig().setCode(Intents.PUBLIC_INTENTS.and(Intents.PUBLIC_GROUP_INTENTS));
        else starter.getConfig().setCode(code);
        if (upUrl != null) starter.getConfig().setInterceptor0(bytes -> {
            try {
                String url = Jsoup.connect(upUrl)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.67")
                        .data("file", "temp.jpg", new ByteArrayInputStream(bytes)).method(Connection.Method.POST).execute().body();
                url = JSONObject.parseObject(url).getString("msg");
                return url;
            } catch (IOException e) {
                return e.getMessage();
            }
        });
        new File("./logs").mkdirs();
        starter.registerListenerHost(new ListenerHost() {
            boolean isFirst = true;
            GenshinUidClient client;

            @Override
            public void handleException(Throwable e) {
                super.handleException(e);
            }

            @EventReceiver
            public void onConnect(ConnectedEvent event) {
                if (isFirst) {
                    isFirst = false;
                    try {
                        client = new GenshinUidClient(new URI(String.format("ws://%s:%s/ws/bot", wssIp, wssPort)), starter);
                        Public.EXECUTOR_SERVICE.submit(client);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            @EventReceiver
            public void onMessage(MessageEvent event) {
                if (!client.isOpen()) return;
                client.queue.offer(event);
                List<MessageData> list = new LinkedList<>();
                for (SendAble e : event.getMessage()) {
                    MessageData message = new MessageData();
                    if (e instanceof PlainText) {
                        message.setType("text");
                        String data = e.toString().trim();
                        if (data.startsWith("/") && data.length() > 1) data = data.substring(1);
                        message.setData(data);
                    } else if (e instanceof Image) {
                        Image image = (Image) e;
                        message.setType("image");
                        message.setData(image.getUrl().startsWith("http") ? image.getUrl() : "https://" + image.getUrl());
                    } else if (e instanceof At) {
                        At at = (At) e;
                        message.setType("at");
                        message.setData(at.getTargetId().toString());
                    }
                    list.add(message);
                }
                MessageReceive receive = new MessageReceive();
                receive.setBot_id("qqguild");
                receive.setBot_self_id(event.getBot().getId());
                receive.setUser_id(event.getSender().getId());
                receive.setMsg_id(event.getRawMessage().getId());
                receive.setUser_type("direct");
                receive.setGroup_id("");
                if (event instanceof BaseMessageChannelReceiveEvent || event instanceof GroupMessageEvent) {
                    if (event instanceof GroupMessageEvent) receive.setBot_id("qqgroup");
                    receive.setUser_type("group");
                    receive.setGroup_id(event.getSubject().getId());
                }
                receive.setUser_pm(2);
                receive.setContent(list.toArray(new MessageData[0]));
                client.send(JSON.toJSONString(receive).getBytes(StandardCharsets.UTF_8));
            }
        });
        starter.APPLICATION.logger.setPrefix("[qqbot.gsuid]");
        starter.run();
        starter.APPLICATION.logger.setPrefix("[qqbot.gsuid]");
    }

    private Starter starter = null;
    private Logger logger = null;
    private Queue<MessageEvent> queue = new ArrayDeque<>(50);

    public GenshinUidClient(URI serverUri, Starter starter) {
        super(serverUri);
        this.starter = starter;
        this.logger = starter.APPLICATION.logger;
    }

    @Override
    public void onOpen(ServerHandshake sh) {
        starter.APPLICATION.logger.info("The WS(gsuid_core) connection is successful!");
    }

    @Override
    public void onMessage(String msg) {
        MessageOut out = JSONObject.parseObject(msg, MessageOut.class);
        String bsid = out.getBot_self_id();
        logger.info(String.format("gsuid_core msg bot(%s) to size: %s;\n%s", bsid, msg.length(), out.toString()));
        for (MessageEvent raw : queue) {
            String msgId = raw.getRawMessage().getId();
            if (Judge.isEmpty(out.getMsg_id())) return;
            MessageAsyncBuilder builder = new MessageAsyncBuilder();
            if (raw instanceof MessageChannelReceiveEvent) {
                builder.append(new At(At.MEMBER_TYPE, raw.getSender().getId()));
                builder.append(new PlainText("\n"));
            }
            for (MessageData d0 : out.getContent()) {
                if (d0.getType().equals("node")) {
                    try {
                        JSONArray array = (JSONArray) d0.getData();
                        for (MessageData d1 : array.toJavaList(MessageData.class)) {
                            builderAppend(builder, d1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else builderAppend(builder, d0);
            }
            raw.send(builder.build());
        }
    }

    private void builderAppend(MessageAsyncBuilder builder, MessageData d0) {
        if (d0.getType().equals("text")) {
            builder.append(new PlainText(d0.getData().toString().trim()));
        } else if (d0.getType().equals("image")) {
            byte[] bytes;
            if (d0.getData().toString().startsWith("base64://")) {
                bytes = Base64.getDecoder().decode(d0.getData().toString().substring("base64://".length()));
            } else {
                bytes = Base64.getDecoder().decode(d0.getData().toString());
            }
            builder.append(new Image(bytes));
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String data = new String(bytes.array(), Charset.forName("utf-8"));
        onMessage(data);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.error("gsuid_core ws close wait 10(s) reconnect code:" + code + " reason:" + reason);
        Public.EXECUTOR_SERVICE.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(10);
                reconnect();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onError(Exception ex) {
        logger.error(PartUtils.getExceptionLine(ex));
    }
}
