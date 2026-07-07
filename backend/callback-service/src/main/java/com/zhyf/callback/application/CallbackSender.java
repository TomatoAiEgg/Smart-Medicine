package com.zhyf.callback.application;

public interface CallbackSender {

    CallbackSendResult send(CallbackRecords.CallbackRecord record);
}
