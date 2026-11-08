package com.mysocial.mesh.emergency

import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.PostWithAuthor
import kotlinx.coroutines.flow.Flow

data class ChannelInfo(
    val tag: String,
    val icon: String,
    val description: String
)

object EmergencyChannels {
    val CHANNELS = listOf(
        ChannelInfo("#emergency-sos", "🚨", "Critical emergency SOS broadcasts & rescue requests"),
        ChannelInfo("#medical-aid", "🩺", "First aid, medication needs, and triage coordination"),
        ChannelInfo("#shelter-updates", "⛺", "Shelter availability, evacuation points, and camps"),
        ChannelInfo("#food-and-water", "🍞", "Potable water supply, ration points, and relief goods"),
        ChannelInfo("#lost-and-found", "🔍", "Missing persons, reunification, and lost items"),
        ChannelInfo("#power-and-fuel", "⚡", "Generators, charging stations, and fuel supply")
    )
    const val DEFAULT_CHANNEL = "#emergency-sos"
}

class EmergencyChannelManager(
    private val db: MeshDatabase
) {
    fun getChannels(): List<ChannelInfo> = EmergencyChannels.CHANNELS

    fun observeChannelFeed(channelTag: String): Flow<List<PostWithAuthor>> =
        db.postViewDao().observeChannelFeed(channelTag)
}

