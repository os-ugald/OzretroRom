package cl.gakuto.kizuna.integration

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import cl.gakuto.kizuna.data.CollectionEntity
import cl.gakuto.kizuna.data.CollectionKind
import cl.gakuto.kizuna.data.CollectionMembershipEntity
import cl.gakuto.kizuna.data.OzDatabase
import org.json.JSONArray
import org.json.JSONObject

class CollectionTransfer(private val context: Context, private val db: OzDatabase) {
    suspend fun exportJson(uri: Uri) {
        val collections = db.collectionDao().all()
        val memberships = db.collectionDao().memberships()
        val root = JSONObject().put("schema", 1).put("brand", "Oz RetroRom").put("version", "0.6.0-beta.4")
        root.put("collections", JSONArray().apply { collections.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("description",it.description).put("isSystem",it.isSystem).put("kind",it.kind)) } })
        root.put("memberships", JSONArray().apply { memberships.forEach { put(JSONObject().put("collectionId",it.collectionId).put("gameId",it.gameId)) } })
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(root.toString(2)) }
    }

    suspend fun importJson(uri: Uri): Pair<Int, Int> {
        val root = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { JSONObject(it.readText()) }
        require(root.optInt("schema", 0) == 1) { "Esquema de colección no compatible" }
        val collectionsJson = root.optJSONArray("collections") ?: JSONArray()
        val membershipsJson = root.optJSONArray("memberships") ?: JSONArray()
        val existing = db.collectionDao().all().associateBy { it.id }
        val collections = (0 until collectionsJson.length()).mapNotNull { i ->
            val o = collectionsJson.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val prior = existing[id]
            CollectionEntity(
                id = id,
                name = o.optString("name", prior?.name ?: id),
                description = o.optString("description", prior?.description ?: "Colección importada"),
                icon = prior?.icon ?: "✦",
                accent = prior?.accent ?: "#00E5FF",
                isSystem = prior?.isSystem ?: o.optBoolean("isSystem", false),
                sortOrder = prior?.sortOrder ?: 1000 + i,
                kind = prior?.kind ?: o.optString("kind", CollectionKind.MANUAL.name),
            )
        }
        val memberships = (0 until membershipsJson.length()).mapNotNull { i ->
            val o = membershipsJson.optJSONObject(i) ?: return@mapNotNull null
            val collectionId = o.optString("collectionId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val gameId = o.optString("gameId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CollectionMembershipEntity(collectionId, gameId)
        }
        db.withTransaction {
            if (collections.isNotEmpty()) db.collectionDao().insertAll(collections)
            if (memberships.isNotEmpty()) db.collectionDao().addMemberships(memberships)
        }
        return collections.size to memberships.size
    }

    suspend fun exportCsv(uri: Uri) {
        val collections = db.collectionDao().all().associateBy { it.id }
        val memberships = db.collectionDao().memberships()
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { out ->
            out.appendLine("collection_id,collection_name,game_id")
            memberships.forEach { m -> out.appendLine(listOf(m.collectionId, collections[m.collectionId]?.name.orEmpty(), m.gameId).joinToString(",") { csv(it) }) }
        }
    }
    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
}
