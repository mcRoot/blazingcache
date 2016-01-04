/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package blazingcache.network.jvm;

import blazingcache.network.Channel;
import blazingcache.network.Message;
import blazingcache.network.ReplyCallback;
import blazingcache.network.SendResultCallback;
import blazingcache.network.netty.DodoMessageUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;


/**
 * In-JVM comunications
 *
 * @author enrico.olivelli
 */
public class JVMChannel extends Channel {

    private volatile boolean active = false;
    private final Map<String, ReplyCallback> pendingReplyMessages = new ConcurrentHashMap<>();
    private final Map<String, Message> pendingReplyMessagesSource = new ConcurrentHashMap<>();
    private JVMChannel otherSide;
    private final ExecutorService callbackexecutor = Executors.newCachedThreadPool();
    private final ExecutorService executionserializer = Executors.newFixedThreadPool(1);
    private String id = UUID.randomUUID().toString();

    @Override
    public String toString() {
        return "JVMChannel{" + "active=" + active + ", id=" + id + '}';
    }

    public JVMChannel() {
    }

    private Message cloneMessage(Message message) {
        ByteBuf buf = Unpooled.buffer();
        DodoMessageUtils.encodeMessage(buf, message);
        return DodoMessageUtils.decodeMessage(buf);
    }

    private void receiveMessageFromPeer(Message message) {
        message = cloneMessage(message);
        if (message.getReplyMessageId() != null) {
            handleReply(message);
        } else {
            try {
                messagesReceiver.messageReceived(message);
            } catch (Throwable t) {
                t.printStackTrace();
                close();
            }
        }
    }

    public void setOtherSide(JVMChannel brokerSide) {
        this.otherSide = brokerSide;
        this.active = true;
    }

    @Override
    public void sendOneWayMessage(Message message, SendResultCallback callback) {
        message.setMessageId(UUID.randomUUID().toString());
        Message _message = cloneMessage(message);
//        System.out.println("[JVM] sendOneWayMessage " + message);
        if (!active || executionserializer.isShutdown()) {
            return;
        }
        executionserializer.submit(() -> {
            otherSide.receiveMessageFromPeer(_message);
            sumitCallback(() -> {
                callback.messageSent(_message, null);
            });
        });

    }

    private void handleReply(Message anwermessage) {

        final ReplyCallback callback = pendingReplyMessages.get(anwermessage.getReplyMessageId());
//        System.out.println("[JVM] handleReply " + anwermessage + " callback=" + callback + ", pendingReplyMessages=" + pendingReplyMessages);
        if (callback != null) {
            pendingReplyMessages.remove(anwermessage.getReplyMessageId());
            Message original = pendingReplyMessagesSource.remove(anwermessage.getReplyMessageId());
            if (original != null) {
                sumitCallback(() -> {
                    callback.replyReceived(original, anwermessage, null);
                });
            }
        }
    }

    @Override
    public void sendReplyMessage(Message inAnswerTo, Message message) {
        message.setMessageId(UUID.randomUUID().toString());
        Message _message = cloneMessage(message);
        if (executionserializer.isShutdown()) {
            System.out.println("[JVM] channel shutdown, discarding reply message " + _message);
            return;
        }
        executionserializer.submit(() -> {
//        System.out.println("[JVM] sendReplyMessage inAnswerTo=" + inAnswerTo.getMessageId() + " newmessage=" + message);
            if (!active) {
                System.out.println("[JVM] channel not active, discarding reply message " + _message);
                return;
            }            
            _message.setReplyMessageId(inAnswerTo.messageId);
            otherSide.receiveMessageFromPeer(_message);
        });
    }

    private void sumitCallback(Runnable r) {
        try {
            callbackexecutor.submit(r);
        } catch (RejectedExecutionException discard) {
        }
    }

    @Override
    public void sendMessageWithAsyncReply(Message message, ReplyCallback callback) {
        message.setMessageId(UUID.randomUUID().toString());
        Message _message = cloneMessage(message);
        if (executionserializer.isShutdown()) {
            System.out.println("[JVM] channel shutdown, discarding sendMessageWithAsyncReply");
            return;
        }
        executionserializer.submit(() -> {
//        System.out.println("[JVM] sendMessageWithAsyncReply " + message);
            if (!active) {
                callbackexecutor.submit(() -> {
                    callback.replyReceived(_message, null, new Exception("connection is not active"));
                });
                return;
            }
            pendingReplyMessages.put(_message.getMessageId(), callback);
            pendingReplyMessagesSource.put(_message.getMessageId(), _message);
            otherSide.receiveMessageFromPeer(_message);
        });
    }

    @Override
    public boolean isValid() {
        return active;
    }

    @Override
    public void close() {
        active = false;
        pendingReplyMessages.forEach((key, callback) -> {
            sumitCallback(() -> {
                Message original = pendingReplyMessagesSource.remove(key);
                if (original != null) {
                    callback.replyReceived(original, null, new Exception("comunication channel closed"));
                }
            });
        });
        pendingReplyMessages.clear();

        if (otherSide.active) {
            otherSide.close();
        }
        executionserializer.shutdown();
        callbackexecutor.shutdown();
        messagesReceiver.channelClosed();
    }

}