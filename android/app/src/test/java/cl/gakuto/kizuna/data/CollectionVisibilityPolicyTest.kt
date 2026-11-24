package cl.gakuto.kizuna.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionVisibilityPolicyTest {
    @Test
    fun allGamesIsHiddenFromCollectionsScreen() {
        assertFalse(CollectionVisibilityPolicy.isVisibleInCollections("all-games"))
    }

    @Test
    fun usefulCollectionsRemainVisible() {
        listOf(
            "downloaded-library",
            "favorites",
            "recently-played",
            "my-collection",
            "curated-beat-em-up",
        ).forEach { collectionId ->
            assertTrue(CollectionVisibilityPolicy.isVisibleInCollections(collectionId))
        }
    }
}
