package cl.gakuto.kizuna.util

object ThumbnailUrl {
    fun boxArt(repository: String, title: String): String =
        LibretroThumbnailHelper.buildUrl(repository, "Named_Boxarts", title)
}
