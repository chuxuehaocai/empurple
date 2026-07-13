package dev.naominet.empurple

import dev.naominet.purple.framework.beans.TextMessageBean
import java.io.File

object ChatStealer {
    fun saveTheChat(msg: TextMessageBean){
        val data = msg.raw_message.replace(Regex("""\[CQ:[^\]]+]"""), "").trimStart()
        if(data.isNotEmpty()){
            File("chat.txt").appendText("$data\n")
        }
    }
}