package com.hari.tvremote.protocol

import com.hari.tvremote.proto.RemoteAppLinkLaunchRequest
import com.hari.tvremote.proto.RemoteConfigure
import com.hari.tvremote.proto.RemoteDeviceInfo
import com.hari.tvremote.proto.RemoteDirection
import com.hari.tvremote.proto.RemoteEditInfo
import com.hari.tvremote.proto.RemoteImeBatchEdit
import com.hari.tvremote.proto.RemoteImeObject
import com.hari.tvremote.proto.RemoteKeyCode
import com.hari.tvremote.proto.RemoteKeyInject
import com.hari.tvremote.proto.RemoteMessage
import com.hari.tvremote.proto.RemotePingResponse
import com.hari.tvremote.proto.RemoteSetActive

/** Builders for every message the phone sends. Field values mirror androidtvremote2/remote.py. */
object Messages {
    fun configureReply(features: Int): RemoteMessage = RemoteMessage.newBuilder()
        .setRemoteConfigure(
            RemoteConfigure.newBuilder()
                .setCode1(features)
                .setDeviceInfo(
                    RemoteDeviceInfo.newBuilder()
                        .setUnknown1(1)
                        .setUnknown2("1")
                        .setPackageName("atvremote")
                        .setAppVersion("1.0.0"),
                ),
        ).build()

    fun setActiveReply(features: Int): RemoteMessage = RemoteMessage.newBuilder()
        .setRemoteSetActive(RemoteSetActive.newBuilder().setActive(features))
        .build()

    fun pingResponse(val1: Int): RemoteMessage = RemoteMessage.newBuilder()
        .setRemotePingResponse(RemotePingResponse.newBuilder().setVal1(val1))
        .build()

    fun key(code: RemoteKeyCode, direction: RemoteDirection): RemoteMessage = RemoteMessage.newBuilder()
        .setRemoteKeyInject(RemoteKeyInject.newBuilder().setKeyCode(code).setDirection(direction))
        .build()

    /**
     * Replaces the focused TV text field with [text] (remote.py send_text): one edit with
     * insert=1 and start = end = text.length - 1, using the counters from the TV's last batch edit.
     */
    fun text(text: String, imeCounter: Int, fieldCounter: Int): RemoteMessage {
        val cursor = text.length - 1
        val edit = RemoteEditInfo.newBuilder()
            .setInsert(1)
            .setTextFieldStatus(RemoteImeObject.newBuilder().setStart(cursor).setEnd(cursor).setValue(text))
        return RemoteMessage.newBuilder()
            .setRemoteImeBatchEdit(
                RemoteImeBatchEdit.newBuilder()
                    .setImeCounter(imeCounter)
                    .setFieldCounter(fieldCounter)
                    .addEditInfo(edit),
            ).build()
    }

    fun appLink(link: String): RemoteMessage = RemoteMessage.newBuilder()
        .setRemoteAppLinkLaunchRequest(RemoteAppLinkLaunchRequest.newBuilder().setAppLink(link))
        .build()
}
