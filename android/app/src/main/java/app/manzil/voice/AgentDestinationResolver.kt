package app.manzil.voice

import app.manzil.offline.Place
import java.util.Locale

/** Resolve against actual saved places; ambiguous names stay in the search UI. */
object AgentDestinationResolver {
    private fun normalize(text:String)=text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun searchQuery(query:String)=query.trim().replace(Regex(",?\\s+Chennai(?:,?\\s+India)?$",RegexOption.IGNORE_CASE),"").trim()
    fun resolve(query:String,places:List<Place>):Place? {
        val q=normalize(query).removeSuffix(" chennai").trim();if(q.length<2)return null
        val exact=places.filter{normalize(it.name)==q}.distinctBy{Pair(it.latitude,it.longitude)}
        if(exact.size==1)return exact.first()
        if(exact.size>1)return null
        val road=places.filter{normalize(it.name)=="$q road"}.distinctBy{Pair(it.latitude,it.longitude)}
        return road.singleOrNull()
    }
}
